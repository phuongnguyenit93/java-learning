<a id="back-to-top"></a>

# Phụ thuộc linh hoạt

## Menu
- [Tiêm mảng, List, Set và Map&lt;String, T&gt;](#flexible-dependencies-001)
- [Thứ tự tập hợp với Ordered, @Order và @Priority](#flexible-dependencies-002)
- [Lọc tập hợp bằng qualifier và phân biệt với @Primary](#flexible-dependencies-003)
- [Biểu diễn phụ thuộc tùy chọn bằng Optional](#flexible-dependencies-004)
- [Tra cứu trì hoãn bằng ObjectFactory và ObjectProvider](#flexible-dependencies-005)
- [getObject, getIfAvailable và getIfUnique](#flexible-dependencies-006)
- [Duyệt nhiều ứng viên bằng stream và orderedStream](#flexible-dependencies-007)
- [Dùng jakarta.inject.Provider và chọn thời điểm lấy bean](#flexible-dependencies-008)
- [Giới hạn sử dụng provider để tránh che giấu phụ thuộc](#flexible-dependencies-009)

## <a id="flexible-dependencies-001">Tiêm mảng, List, Set và Map&lt;String, T&gt;</a>

<details>
<summary>Xem chi tiết</summary>

# Tiêm mảng, List, Set và Map<String, T> trong Spring Core Container

## Mục tiêu học tập

Khi một chức năng cần **nhiều implementation cùng giao diện**, Spring có thể đưa tất cả bean phù hợp vào một dependency thay vì buộc ta chọn một bean duy nhất. Sau bài này, bạn sẽ biết cách tiêm `T[]`, `List<T>`, `Set<T>` và `Map<String, T>`; hiểu tên bean, cách lọc bằng qualifier, thứ tự thực thi, ứng xử khi không có bean và các lỗi dễ gặp.

Ví dụ ứng dụng: hệ thống xử lý đơn hàng có nhiều bước kiểm tra; mỗi bước là một bean triển khai `OrderRule`. Service nhận `List<OrderRule>` rồi duyệt từng bước, không cần tự viết danh sách cố định các class triển khai.

## 1. Bốn hình thức tập hợp phụ thuộc

Giả sử trong Spring container có ba bean cùng triển khai interface `Handler`. Một injection point có thể khai báo:

```java
Handler[] handlers;
List<Handler> handlers;
Set<Handler> handlers;
Map<String, Handler> handlersByName;
```

| Kiểu nhận | Giá trị được tiêm | Dùng khi |
| --- | --- | --- |
| `Handler[]` | Mảng chứa các bean `Handler` phù hợp | Tích hợp API Java đang dùng mảng |
| `List<Handler>` | Danh sách các bean phù hợp | Pipeline cần duyệt theo thứ tự xác định |
| `Set<Handler>` | Tập hợp các bean phù hợp | Quan tâm tính tập hợp, không nên phụ thuộc thứ tự duyệt |
| `Map<String, Handler>` | Khóa là **tên bean**, giá trị là bean tương ứng | Chọn chiến lược theo tên bean hoặc tra cứu handler |

Đây là **multi-bean autowiring**: Spring tìm nhiều bean có kiểu phù hợp với `T`. Nó khác việc tự khai báo một bean riêng có giá trị là `List<T>` hoặc một `Map` cấu hình. Kiểu phần tử vẫn quan trọng: `List<Handler>` không có nghĩa là lấy mọi bean trong ứng dụng.

Các điểm tiêm thông dụng là tham số constructor, tham số của phương thức `@Bean`, setter có `@Autowired` hoặc field có `@Autowired`. Với bean chỉ có **một constructor**, Spring tự dùng constructor đó mà không cần đặt `@Autowired`.

## 2. Spring lựa chọn phần tử như thế nào?

Một cách hiểu thực dụng về quá trình phân giải dependency:

1. Spring có các bean definition được đăng ký qua `@Component`/component scan, `@Bean` trong configuration hoặc cách đăng ký tương đương. Chỉ đối tượng được container quản lý mới tham gia autowiring.
2. Với `List<T>`, `T[]` hoặc `Set<T>`, Spring tìm các bean là ứng viên autowire có kiểu tương thích `T`. Với `Map<String, T>`, nó tìm theo kiểu của **giá trị `T`** rồi dùng **bean name** làm khóa.
3. Spring xét metadata của injection point, bao gồm qualifier và thông tin generic khi có. `@Qualifier` có thể **lọc nhiều bean cùng lúc**, không chỉ chọn một bean.
4. Container cung cấp tập kết quả. `@Order`/`Ordered` ảnh hưởng thứ tự phần tử của mảng và danh sách; các tập hợp khác không nên được dùng như hợp đồng thứ tự xử lý.

**Chú ý về `@Primary`:** Với `Handler handler` (đơn lẻ), `@Primary` giúp chọn một bean giữa nhiều ứng viên. Với `List<Handler>`/mảng/set/map, các bean phù hợp **vẫn cùng xuất hiện**; `@Primary` không biến danh sách thành một phần tử. `@Qualifier` mới là công cụ phù hợp để thu hẹp nhóm.

Bean name có thể do `@Bean("name")`, `@Component("name")` hoặc quy tắc đặt tên mặc định tạo ra. Với map tự động tiêm, khóa **không phải** tên class, giá trị trả về từ `handler.id()` hay tên qualifier.

## 3. Ví dụ chạy đầy đủ với Java 17 và Spring Framework 6.2

Ví dụ sau đăng ký ba `Handler`, tiêm đủ bốn kiểu, lọc nhóm `fast` và đối chiếu với một dependency đơn lẻ dùng `@Primary`. Dùng Spring Framework thuần, không phụ thuộc Spring Boot.

Cấu trúc dự án:

```text
spring-collection-injection/
├── pom.xml
└── src/main/java/demo/CollectionInjectionDemo.java
```

`pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-collection-injection</artifactId>
    <version>1.0-SNAPSHOT</version>
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
```

`src/main/java/demo/CollectionInjectionDemo.java`:

```java
package demo;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;

public class CollectionInjectionDemo {

    interface Handler {
        String id();
    }

    @Configuration(proxyBeanMethods = false)
    static class AppConfig {

        @Bean("alphaHandler")
        @Order(20)
        @Qualifier("fast")
        Handler alphaHandler() {
            return () -> "A";
        }

        @Bean("betaHandler")
        @Order(10)
        @Primary
        @Qualifier("fast")
        Handler betaHandler() {
            return () -> "B";
        }

        @Bean("gammaHandler")
        @Order(30)
        @Qualifier("slow")
        Handler gammaHandler() {
            return () -> "C";
        }

        @Bean
        Report report(
                Handler[] array,
                List<Handler> list,
                Set<Handler> set,
                Map<String, Handler> byName,
                @Qualifier("fast") List<Handler> fast,
                Handler preferred) {
            return new Report(array, list, set, byName, fast, preferred);
        }
    }

    record Report(
            Handler[] array,
            List<Handler> list,
            Set<Handler> set,
            Map<String, Handler> byName,
            List<Handler> fast,
            Handler preferred) {

        void print() {
            System.out.println("Array: " + ids(Arrays.asList(array)));
            System.out.println("List: " + ids(list));
            System.out.println("Set size: " + set.size());
            System.out.println("Map keys (sorted): " + new TreeSet<>(byName.keySet()));
            System.out.println("Fast: " + ids(fast));
            System.out.println("Single (@Primary): " + preferred.id());
        }
    }

    private static String ids(Collection<Handler> handlers) {
        return handlers.stream().map(Handler::id)
                .collect(Collectors.joining(","));
    }

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            context.getBean(Report.class).print();
        }
    }
}
```

Chạy trong thư mục chứa `pom.xml` (yêu cầu JDK 17+ và Maven):

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.CollectionInjectionDemo
```

Các dòng do chương trình in ra (không tính log của Maven hoặc thư viện nếu môi trường bật log):

```text
Array: B,A,C
List: B,A,C
Set size: 3
Map keys (sorted): [alphaHandler, betaHandler, gammaHandler]
Fast: B,A
Single (@Primary): B
```

Giải thích từng kết quả:

- Mảng và `List` chứa cả ba handler, theo thứ tự `@Order(10)`, `@Order(20)`, `@Order(30)`. Giá trị **nhỏ hơn đi trước**.
- `Set` chứa các bean phù hợp. Ví dụ chỉ in kích thước, vì logic ứng dụng không nên giả định thứ tự duyệt `Set` giống `List`.
- `Map<String, Handler>` có khóa là ba **bean name** đã khai báo. `TreeSet` trong lệnh in chỉ **sắp xếp tên để output cố định**, không chứng minh map được Spring cấp theo thứ tự chữ cái hay `@Order`.
- Tham số `@Qualifier("fast") List<Handler> fast` chỉ nhận hai bean có qualifier `fast`, rồi danh sách vẫn chịu quy tắc `@Order`.
- `Handler preferred` chỉ có một giá trị và chọn `betaHandler` nhờ `@Primary`. Dấu `@Primary` trên `betaHandler` **không loại** nó hoặc các bean khác khỏi các tập hợp.

Trong một bean do `@Component` quản lý, cách viết thường gặp là constructor injection:

```java
// Đoạn minh họa: cần có Handler là kiểu đã định nghĩa trong ứng dụng.
@Component
class HandlerPipeline {
    private final List<Handler> handlers;

    HandlerPipeline(List<Handler> handlers) {
        this.handlers = handlers;
    }

    void execute() {
        handlers.forEach(handler -> System.out.println(handler.id()));
    }
}
```

Ở đoạn trên, import `@Component` là `org.springframework.stereotype.Component`. Class cần nằm trong phạm vi component scan, còn `Handler` và các implementation phải được đăng ký bean. Đoạn ngắn này là **minh họa để đưa vào dự án**, không phải chương trình chạy độc lập.

## 4. Lọc tập hợp bằng `@Qualifier`

`@Qualifier` áp dụng được cho collection injection. Ví dụ hai bean đều mang nhãn `fast`; Spring sẽ đưa **cả hai** vào `List<Handler>` có `@Qualifier("fast")`. Không yêu cầu giá trị qualifier phải duy nhất.

Có thể gắn qualifier vào bean định nghĩa qua `@Component` hoặc phương thức `@Bean`; phía nhận đặt trên field, constructor parameter hoặc method parameter. Trước hết Spring vẫn khớp kiểu `Handler`, sau đó qualifier thu hẹp tập ứng viên. Dù qualifier trùng một bean name, qualifier vẫn được hiểu là điều kiện lọc trong tập bean đã khớp kiểu, không phải cơ chế bỏ qua kiểm tra kiểu.

Cú pháp triển khai phổ biến (đoạn minh họa, dùng trong bean Spring):

```java
@Autowired
@Qualifier("fast")
private List<Handler> fastHandlers;

// Tương đương về ý tưởng khi dùng constructor:
// MyService(@Qualifier("fast") List<Handler> fastHandlers) { ... }
```

Ưu tiên constructor injection cho dependency bắt buộc vì dễ test, giữ trường `final` và bộc lộ rõ hợp đồng của class.

## 5. Thứ tự: `@Order`, `Ordered`, `@Priority` và `@DependsOn`

Khi chạy pipeline, **thứ tự là một phần của nghiệp vụ**. Đối với mảng và `List<T>` được autowire, Spring nhận thứ tự từ `org.springframework.core.Ordered`, `@Order` hoặc chuẩn `@Priority`. Số nhỏ hơn có độ ưu tiên cao hơn; nếu không khai báo thứ tự, mặc định thường theo thứ tự đăng ký bean, nhưng không nên dựa vào đó như quy ước nghiệp vụ bền vững.

```java
@Bean
@Order(100)
Handler lastHandler() {
    return () -> "last";
}
```

`@Order` có thể đặt trên class component hoặc từng phương thức `@Bean`. Nếu nhiều `@Bean` tạo instance cùng một class, đặt trên **mỗi phương thức** giúp xác định thứ tự của từng bean. `jakarta.annotation.Priority` là một lựa chọn ở cấp class; annotation chuẩn này không khai báo trên phương thức `@Bean`. Có thể dùng `Ordered` khi mức ưu tiên cần được khai báo qua đối tượng.

Đừng nhầm:

- `@Order` điều khiển **thứ tự khi lấy nhiều bean** hoặc một số cơ chế sắp xếp của framework; nó **không** bắt singleton phải khởi tạo theo thứ tự đó.
- `@DependsOn` nói về **phụ thuộc khởi tạo/hủy bean**, không phải thứ tự phần tử trong `List<T>`.
- `@Primary` chủ yếu giải quyết **một injection point kiểu đơn lẻ** khi có nhiều ứng viên, không phải bộ lọc danh sách.
- Với `Set<T>` và `Map<String, T>`, nếu business flow bắt buộc có thứ tự, hãy dùng `List<T>` với `@Order`, hoặc tự sắp xếp một bản sao theo comparator rõ ràng. Không dựa vào thứ tự duyệt của set/map.

## 6. Generic type là một phần điều kiện chọn

Spring có thể dùng thông tin generic của bean và injection point để tìm đúng ứng viên. Ví dụ `Store<String>` và `Store<Integer>` không nhất thiết thuộc cùng một nhóm khi yêu cầu `List<Store<String>>`.

```java
// Đoạn minh họa bên trong một @Configuration;
// giả sử đã khai báo interface Store<T> { T get(); }
@Bean
Store<String> textStore() {
    return () -> "ready";
}

@Bean
Store<Integer> countStore() {
    return () -> 42;
}

// Trong bean tiêu thụ:
@Autowired
List<Store<String>> stringStores; // chỉ các Store<String> phù hợp
```

Điều này dựa trên metadata generic mà Spring nhận biết từ khai báo bean, chẳng hạn kiểu trả về của `@Bean`. **Không dùng raw type** như `List` hoặc `Map` rồi kỳ vọng Spring suy ra `T`. Với `Map<String, Store<String>>`, `String` đầu tiên là **kiểu khóa tên bean**, còn `Store<String>` là kiểu của giá trị cần lọc.

Nếu tự tạo bean là một `List<Handler>` (thay vì nhiều bean `Handler`), cần phân biệt hai mục đích: **tiêm một collection bean đã định nghĩa** và **thu thập nhiều bean phần tử**. Spring có thể so khớp collection bean bằng thông tin kiểu, nhưng nếu mục tiêu là tham chiếu **chính collection bean theo tên**, có thể dùng `@Resource(name = "...")` trên field/setter hoặc cấu hình tường minh. Cách tiêm đó không mang nghĩa lấy tất cả các implementation. Với tham số constructor, `@Qualifier` hoặc cấu hình tường minh phù hợp hơn `@Resource`.

## 7. Khi không tìm được bean `T`

Đây là trường hợp cần phân biệt **nơi tiêm** và quy tắc bắt buộc:

| Tình huống | Hành vi cần lưu ý |
| --- | --- |
| Field hoặc setter `@Autowired` mặc định, tập ứng viên rỗng | Injection point bắt buộc có thể gây lỗi tạo bean (`UnsatisfiedDependencyException`/lỗi liên quan). Theo tài liệu Spring, đa phần cần ít nhất một phần tử khớp. |
| Field hoặc setter `@Autowired(required = false)`, không có ứng viên | Spring bỏ qua tiêm; field giữ giá trị ban đầu, setter có thể không được gọi. Không tự động khẳng định field nhận collection rỗng. |
| **Một constructor duy nhất** của bean, tham số array/collection/map không có phần tử | Spring có quy tắc đặc biệt: multi-element parameter có thể nhận container **rỗng**. Đây là ngoại lệ quan trọng khi thiết kế plugin tùy chọn. |
| Truy cập động với `ObjectProvider<T>` | Có thể dùng `orderedStream()` để truy vấn các bean phù hợp tại thời điểm sử dụng. |

Ví dụ field tùy chọn giữ mặc định:

```java
// Minh họa nằm trong một bean được Spring quản lý:
@Autowired(required = false)
private List<Handler> optionalHandlers = List.of();
```

Nếu muốn diễn đạt rõ dependency có thể thiếu và có tính động/lazy, `ObjectProvider<T>` hữu ích:

```java
// Minh họa: import org.springframework.beans.factory.ObjectProvider;
private final ObjectProvider<Handler> handlerProvider;

MyService(ObjectProvider<Handler> handlerProvider) {
    this.handlerProvider = handlerProvider;
}

void process() {
    handlerProvider.orderedStream().forEach(handler -> {
        // Gọi từng handler theo thứ tự Spring hỗ trợ.
    });
}
```

Đoạn này cần đặt vào class `MyService` do Spring quản lý. Nó cho phép hỏi container lúc dùng thay vì giữ sẵn một danh sách tại thời điểm khởi tạo.

Lưu ý thêm về scope: nếu tiêm thẳng một `List<PrototypeBean>` vào singleton, không có nghĩa mỗi lần duyệt list sẽ tự sinh một prototype mới. Việc giải quyết dependency đã xảy ra khi singleton được tạo. Khi cần instance mới theo từng lần dùng, cân nhắc `ObjectProvider` hoặc cơ chế scope phù hợp.

## 8. Phân biệt map bean với map dữ liệu cấu hình

Hai khai báo trông giống nhau nhưng có ý nghĩa khác:

```java
@Autowired
Map<String, Handler> handlersByName;  // tên bean -> instance Handler
```

Đây là **map các bean**. Các khóa như `alphaHandler` được lấy từ container.

```java
// Khai báo dữ liệu ứng dụng theo cấu hình, không phải ví dụ autowire các Handler:
Map<String, String> endpoints;
```

Nếu `endpoints` chứa dữ liệu YAML/properties như `payments: https://...`, nên dùng cơ chế bind cấu hình (ví dụ `@ConfigurationProperties` trong Spring Boot, hoặc `@Value`/chuyển đổi giá trị thích hợp). Không kỳ vọng tiêm `Map<String, String>` bằng `@Autowired` sẽ tự biến toàn bộ thuộc tính cấu hình thành map.

Ràng buộc đáng nhớ: dạng **đa bean map** đòi khóa `String` vì khóa là bean name. `Map<Long, Handler>` không phải cách yêu cầu Spring tạo map với id số tự động. Nếu cần tra cứu theo khóa nghiệp vụ, hãy xây map riêng:

```java
// Minh họa tạo index nghiệp vụ từ collection đã tiêm:
Map<String, Handler> byBusinessId = handlers.stream()
        .collect(Collectors.toMap(Handler::id, h -> h));
```

Khi dùng `Collectors.toMap`, hãy xử lý id trùng nếu nghiệp vụ cho phép; đoạn trên ném lỗi nếu có hai handler cùng id.

## 9. Thiết kế và vận hành an toàn

**Hãy chọn kiểu dữ liệu theo ý nghĩa sử dụng.** Nếu chạy chuỗi filter/validator/plugin theo thứ tự, `List<T>` là lựa chọn rõ ràng. Nếu cần chọn implementation theo tên đăng ký, dùng `Map<String, T>`. Nếu thư viện yêu cầu mảng, dùng `T[]`. Nếu chỉ cần nhóm đối tượng, `Set<T>` phù hợp, nhưng đừng suy luận `Set` sẽ loại các bean khác nhau chỉ vì chúng có cùng class hoặc cùng id nghiệp vụ.

**Định danh và tính ổn định.** Khóa của `Map<String, T>` phụ thuộc tên bean. Đổi tên `@Bean`, đổi component name hoặc refactor cách đăng ký có thể phá lookup bằng chuỗi. Nếu khóa là hợp đồng nghiệp vụ công khai, khai báo tên bean tường minh hoặc thiết kế registry riêng theo một định danh nghiệp vụ ổn định.

**Tính đóng gói.** Collection đã tiêm là danh sách tham chiếu bean. Tránh thêm/xóa phần tử trực tiếp rồi giả định đã thay đổi đăng ký trong container. Nếu service muốn bảo vệ trạng thái, có thể chụp bản sao (`List.copyOf`, `Map.copyOf`) hoặc cấu trúc phù hợp; nếu cần giữ thứ tự map, chọn cấu trúc có quy ước thứ tự rõ ràng. Mọi tham chiếu vẫn trỏ tới bean gốc, bản sao collection không clone đối tượng bên trong.

**Độ lớn tập ứng viên.** Một interface quá chung có thể thu hút nhiều bean không liên quan. Dùng interface có ý nghĩa nghiệp vụ, qualifier hoặc generic rõ ràng; tránh quét component ngoài phạm vi dự định. Đặc biệt, một bean có `autowireCandidate=false` hay các điều kiện đăng ký khác có thể không tham gia tập ứng viên.

**Thời điểm sử dụng.** Collection constructor injection thường được phân giải khi tạo consumer. Nếu các bean được đăng ký hoặc thay đổi về sau, đừng mặc định collection đã tiêm được cập nhật theo thời gian; nếu thật sự cần tra cứu động, dùng API `ApplicationContext`/`ObjectProvider` có chủ đích.

## 10. Lỗi thường gặp và cách kiểm tra

| Hiện tượng | Nguyên nhân thường gặp | Hướng xử lý |
| --- | --- | --- |
| `NoSuchBeanDefinitionException` hoặc `UnsatisfiedDependencyException` | Không có bean `T` phù hợp; field/setter bắt buộc | Kiểm tra `@Component`, component scan, `@Bean`, qualifier, condition và dạng injection |
| `NoUniqueBeanDefinitionException` khi tiêm `T` đơn lẻ | Có nhiều `T` mà không giải quyết được ứng viên duy nhất | Dùng `@Qualifier` hoặc `@Primary`, hoặc đổi sang multi-bean injection nếu thực sự cần tất cả |
| `List<T>` thiếu bean mong muốn | Bean chưa đăng ký, generic/qualifier không khớp hoặc không là ứng viên autowire | Kiểm tra bean definition và chữ ký kiểu trả về |
| `Map<String, T>` không có khóa mong đợi | Nhầm bean name với class name, qualifier hoặc id nghiệp vụ | Ghi rõ `@Bean("...")` hoặc `@Component("...")` và kiểm tra `keySet()` |
| Pipeline chạy sai thứ tự | Tin vào thứ tự đăng ký hoặc thứ tự duyệt `Set`/`Map` | Dùng `List<T>` và đặt `@Order` tại bean mục tiêu |
| `@Order` bị hiểu là thứ tự tạo singleton | `@Order` chỉ phục vụ cơ chế sắp xếp thích hợp | Dùng quan hệ dependency/`@DependsOn` nếu cần ràng buộc khởi tạo |
| Field tùy chọn là `null` | `@Autowired(required=false)` bỏ qua tiêm | Khởi tạo giá trị mặc định an toàn hoặc đổi sang constructor injection/`ObjectProvider` |
| Map nhận không đúng kiểu dữ liệu | Trộn map tên bean với map properties hoặc dùng khóa không phải `String` | Tách bean registry khỏi cơ chế bind configuration |

Các bước gỡ lỗi ngắn gọn: kiểm tra **bean đã được tạo/đăng ký chưa**; kiểm tra **T và generic**; kiểm tra **qualifier/condition**; cuối cùng kiểm tra **bean name và `@Order`**. Khi test, có thể lấy trực tiếp `context.getBeansOfType(Handler.class)` để quan sát các bean thuộc kiểu đó, nhưng nhớ kết quả tra cứu theo kiểu không tự mô phỏng đầy đủ mọi qualifier của một injection point cụ thể.

## 11. Bài tập tự kiểm tra

1. Thêm bean `deltaHandler` với `@Order(5)` và qualifier `slow` vào ví dụ hoàn chỉnh. Dự đoán `Array`, `List`, `Set size`, `Map keys` và `Fast` thay đổi thế nào.
2. Bỏ `@Primary` khỏi `betaHandler` nhưng giữ tham số `Handler preferred`. Phân tích vì sao tiêm kiểu đơn lẻ có thể thất bại khi nhiều `Handler` khớp, trong khi tập hợp vẫn có thể phân giải.
3. Đổi `@Qualifier("fast")` trên tham số `List<Handler>` thành `@Qualifier("slow")`. Giải thích kết quả mong đợi và lý do `@Order` không thay qualifier.
4. Xây `Map<String, Handler>` theo `Handler.id()` từ `List<Handler>` đã tiêm. Bạn sẽ xử lý thế nào nếu hai bean trả về cùng một id?

**Tự đối chiếu:** Bài 1: `Array`/`List` bắt đầu bằng D (thứ tự `5,10,20,30`), size set tăng thành 4, map có thêm `deltaHandler`, `Fast` vẫn gồm B và A. Bài 2: có nhiều ứng viên cho `Handler` đơn lẻ, cần điều kiện chọn; việc tiêm tập hợp vẫn chứa các bean phù hợp. Bài 3: danh sách lọc chỉ lấy `gammaHandler`. Bài 4: cần chính sách xử lý khóa trùng (báo lỗi, chọn đầu/cuối hoặc thiết kế id duy nhất).

## Tài liệu chính thức

- Spring Framework — [Using `@Autowired`](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired.html): tiêm mảng, collection, map, thứ tự và dependency tùy chọn.
- Spring Framework — [Fine-tuning Annotation-based Autowiring with Qualifiers](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired-qualifiers.html): qualifier là bộ lọc, kể cả trên collection.
- Spring Framework — [Using Generics as Autowiring Qualifiers](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/generics-as-qualifiers.html): lọc ứng viên bằng generic.
- Spring Framework — [`@Autowired` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.0/javadoc-api/org/springframework/beans/factory/annotation/Autowired.html): hợp đồng API của array/collection/map injection.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="flexible-dependencies-002">Thứ tự tập hợp với Ordered, @Order và @Priority</a>

<details>
<summary>Xem chi tiết</summary>

# Thứ tự tập hợp bean với `Ordered`, `@Order` và `@Priority` trong Spring

## 1. Bài toán: nhiều bean cùng kiểu nhưng cần chạy đúng thứ tự

Trong Spring Core Container, một interface có thể có nhiều bean implementation. Khi tiêm `List<T>` hoặc `T[]`, Spring có thể thu thập tất cả bean phù hợp rồi **sắp xếp các phần tử** theo metadata về thứ tự. Điều này hữu ích cho chuỗi xử lý (pipeline), bộ kiểm tra dữ liệu, danh sách plugin, các bước chuẩn hóa hoặc nhóm handler cần chạy tuần tự.

Ví dụ, có ba bước xử lý một đơn hàng:

1. Kiểm tra đầu vào.
2. Tính giá.
3. Ghi log.

Nếu gọi bước tính giá trước khi kiểm tra đầu vào, kết quả có thể sai hoặc phát sinh lỗi. Một `List<OrderStep>` được Spring sắp xếp theo thứ tự khai báo cho phép thêm bước mới mà không cần viết lại toàn bộ cấu trúc pipeline.

Ba cách khai báo thứ tự thường gặp là:

- `org.springframework.core.Ordered`: **interface** cho bean tự cung cấp giá trị thứ tự tại runtime thông qua `getOrder()`.
- `org.springframework.core.annotation.Order`: **annotation của Spring** gắn thứ tự lên lớp hoặc một phương thức `@Bean`.
- `jakarta.annotation.Priority`: **annotation chuẩn Jakarta** đặt lên kiểu (class); Spring hiểu nó như metadata thứ tự, đồng thời nó còn có thể tham gia lựa chọn một ứng viên trong một số tình huống cần một bean.

**Quy ước chung khi sắp xếp:** số **nhỏ hơn đi trước**. Ví dụ `5` được xếp trước `10`, rồi `30`. “Priority cao hơn” ở đây không có nghĩa là giá trị số lớn hơn.

Mục tiêu của bài học là biết chính xác cơ chế áp dụng ở điểm tiêm nhiều phần tử, chọn đúng công cụ cho từng tình huống và tránh nhầm **thứ tự danh sách** với **thứ tự khởi tạo bean**.

## 2. Spring sắp thứ tự ở đâu?

Một dependency nhiều phần tử có thể xuất hiện trong constructor, field hoặc tham số phương thức `@Bean`:

```java
// Trích đoạn minh họa; interface Step và bean sở hữu
// constructor này được khai báo đầy đủ trong ví dụ bên dưới.
public Pipeline(List<Step> steps) {
    this.steps = List.copyOf(steps);
}
```

Khi Spring dựng `Pipeline` và cần `List<Step>`, container sẽ:

1. Tìm các bean có kiểu phù hợp với `Step`, xét cả các điều kiện autowire và qualifier nếu có.
2. Thu thập bean được chọn vào collection.
3. Với `List` hoặc mảng được autowire, sử dụng bộ so sánh thứ tự thích hợp để xếp các bean dựa vào `Ordered`, `@Order` hoặc `@Priority`.
4. Tiêm danh sách/mảng đã được sắp xếp vào đối tượng nhận dependency.

Vì thế, thứ tự **không phụ thuộc** vào việc method `@Bean` nằm trên hay dưới trong file nguồn. Khi có giá trị order khác nhau, Spring sắp dựa trên giá trị đó.

Trong trường hợp **không khai báo bất kỳ giá trị thứ tự nào**, tài liệu Spring mô tả các phần tử theo thứ tự đăng ký bean definition trong container. Tuy nhiên, không nên dùng thứ tự đăng ký làm hợp đồng nghiệp vụ lâu dài: thay đổi cấu hình, component scanning hay cách đăng ký bean có thể làm thứ tự quan sát thay đổi.

### Collection nào có hợp đồng thứ tự đáng tin?

| Điểm tiêm | Cách dùng | Điều cần nhớ |
| --- | --- | --- |
| `List<Step>` | Pipeline cần duyệt các bước có thứ tự | **Khuyến nghị:** Spring hỗ trợ sắp theo order metadata |
| `Step[]` | API nhận mảng các bước | Spring hỗ trợ sắp theo order metadata |
| `Set<Step>` | Tập phần tử không nhấn mạnh thứ tự | Không nên dựa vào thứ tự lặp của `Set` như một hợp đồng thực thi |
| `Map<String, Step>` | Tra cứu bean theo tên | Key là tên bean; đừng đồng nhất thứ tự duyệt map với thứ tự pipeline |

Điểm quan trọng: dùng `List` hoặc mảng khi *thứ tự xử lý là đặc tính nghiệp vụ*. Nếu cần tra cứu theo tên, `Map` phù hợp hơn; nếu cần trình tự thực thi, hãy lấy `List` rồi duyệt `for` hoặc `stream` theo thứ tự đã nhận.

## 3. Quy tắc so sánh giá trị order

Các giá trị thường dùng:

| Metadata | Ví dụ giá trị | Vị trí so với các giá trị khác |
| --- | ---: | --- |
| `@Order(-100)` | -100 | Trước `@Order(0)` |
| `@Order(0)` | 0 | Trước `@Priority(10)` |
| `@Priority(10)` | 10 | Trước `Ordered#getOrder() = 20` |
| `Ordered#getOrder()` | 20 | Trước `@Order(100)` |
| `@Order(100)` | 100 | Trước bean không có order |
| Không khai báo thứ tự | `Ordered.LOWEST_PRECEDENCE` về ngữ nghĩa | Thường ở cuối danh sách đã sắp |

Không có quy tắc “`@Priority` luôn đứng trước `@Order`” chỉ vì tên annotation là Priority. **Giá trị thứ tự mới là yếu tố quyết định** trong so sánh thông thường. `@Order(5)` đứng trước `@Priority(10)`, và `@Priority(10)` đứng trước `Ordered#getOrder() = 20`.

`Ordered` có các hằng số:

```java
Ordered.HIGHEST_PRECEDENCE  // Integer.MIN_VALUE
Ordered.LOWEST_PRECEDENCE   // Integer.MAX_VALUE
```

Số âm hoàn toàn hợp lệ. Tuy vậy, nên dùng những mức dễ hiểu như `0, 10, 20, 30` để chừa khoảng thêm bước mới. Số order là một **thứ hạng so sánh**, không phải số giây, số luồng hay mức ưu tiên của hệ điều hành.

### Nếu trùng giá trị thì sao?

Nếu hai bean đều có `@Order(10)`, comparator không có đủ thông tin từ order để xác định thứ tự tương đối. Trong thực tế, kết quả còn chịu ảnh hưởng thứ tự đầu vào của quá trình sắp xếp; **không được viết nghiệp vụ đòi hỏi bean A luôn trước B chỉ nhờ cả hai cùng order**. Hãy gán hai giá trị riêng nếu thứ tự giữa chúng quan trọng.

### Một interface khác tên gần giống: `PriorityOrdered`

`org.springframework.core.PriorityOrdered` mở rộng `Ordered`, nhưng đây là **interface riêng**, không phải annotation `jakarta.annotation.Priority`. Trong `OrderComparator` của Spring, đối tượng thực thi `PriorityOrdered` được xếp **trước các đối tượng `Ordered` thông thường**, ngay cả khi giá trị `getOrder()` của nó lớn hơn giá trị trên một đối tượng Ordered thường. Cơ chế này thường có ý nghĩa trong xử lý hạ tầng Spring; với các pipeline ứng dụng thông thường, `Ordered` hoặc `@Order` là đủ. Không tự động suy luận thứ tự của `PriorityOrdered` từ ví dụ `@Priority` bên dưới.

## 4. Phân biệt ba cách khai báo

### 4.1. `Ordered`: giá trị thứ tự theo instance

Một bean implement interface của Spring:

```java
import org.springframework.core.Ordered;

final class ConfigurableStep implements Step, Ordered {
    private final int order;

    ConfigurableStep(int order) {
        this.order = order;
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    public String name() {
        return "configurable";
    }
}
```

Đoạn trên **minh họa**, dùng cùng interface `Step` trong chương trình hoàn chỉnh ở mục 5. `getOrder()` có thể trả về một giá trị nhận từ cấu hình, vì thế nhiều instance của cùng một class có thể có thứ tự khác nhau.

Ưu điểm: linh hoạt, xác định theo instance. Đổi lại, class business phải phụ thuộc vào Spring API `Ordered`.

### 4.2. `@Order`: metadata cố định

Khai báo trên class:

```java
@Order(10)
final class ValidationStep implements Step {
    @Override
    public String name() {
        return "validation";
    }
}
```

Hoặc khai báo riêng trên **phương thức `@Bean`**:

```java
@Bean
@Order(5)
Step loggingStep() {
    return new PlainStep("logging");
}
```

Đây là hai đoạn **minh họa** sử dụng `Step` và `PlainStep` được khai báo ở mục 5; các annotation/import phụ trợ có trong chương trình hoàn chỉnh.

Đặt `@Order` trên phương thức `@Bean` đặc biệt hữu ích khi không kiểm soát source class (thư viện bên thứ ba) hoặc một class được khai báo thành nhiều bean với vai trò khác nhau.

`@Order` trên **lớp `@Configuration`** có mục đích khác: ảnh hưởng thứ tự xử lý giữa các configuration class. Nó **không truyền giá trị order** xuống mọi bean tạo bởi các phương thức `@Bean` bên trong. Muốn điều khiển bean trong collection, đặt metadata trên **lớp bean đích** hoặc **từng phương thức `@Bean` tương ứng**.

### 4.3. `@Priority`: annotation chuẩn Jakarta

Ví dụ:

```java
import jakarta.annotation.Priority;

@Priority(30)
final class NotifyStep implements Step {
    @Override
    public String name() {
        return "notify";
    }
}
```

Đây là đoạn **minh họa**, dùng interface `Step` trong chương trình hoàn chỉnh. Với Spring Framework 6+, import đúng là `jakarta.annotation.Priority`. Tránh copy import legacy `javax.annotation.Priority` từ các dự án Spring Framework 5 cũ.

**Lưu ý quan trọng:** `jakarta.annotation.Priority` không cho phép đặt trực tiếp trên phương thức `@Bean`. Muốn gán thứ tự cho một bean được tạo bằng phương thức factory, dùng `@Order` trên phương thức đó. Ngoài thứ tự collection, `@Priority` còn có ngữ nghĩa chọn một ứng viên được ưu tiên khi nhiều bean có thể phù hợp với dependency đơn; không nên nhầm tình huống đó với thứ tự phần tử của một `List`.

### So sánh nhanh

| Đặc điểm | `Ordered` | `@Order` | `@Priority` |
| --- | --- | --- | --- |
| Kiểu | Interface Spring | Annotation Spring | Annotation Jakarta |
| Nơi dùng điển hình | Bean instance triển khai `getOrder()` | Class bean, method `@Bean` | Class bean |
| Order thay đổi theo instance | Có | Không (giá trị annotation cố định) | Không (giá trị annotation cố định) |
| Spring xử lý khi tiêm `List`/array | Có | Có | Có |
| Dùng trực tiếp trên method `@Bean` | Không; instance implement interface | Có | **Không** |
| Liên quan đến chọn một bean | Không dùng thay `@Primary`/qualifier | Không dùng thay `@Primary`/qualifier | Có thể tham gia phân giải ưu tiên trong trường hợp cụ thể |
| Phù hợp nhất khi | Order đến từ cấu hình/runtime | Ứng dụng Spring có thứ tự tĩnh | Tích hợp thư viện hoặc code dùng chuẩn Jakarta |

Nếu một instance vừa implement `Ordered` vừa có annotation `@Order`/`@Priority` trên class, `AnnotationAwareOrderComparator` thông thường ưu tiên giá trị do instance `getOrder()` cung cấp so với annotation tĩnh. Đừng cấu hình đồng thời hai nguồn thứ tự trái ngược nếu không có lý do: chỉ rõ một nguồn là dễ bảo trì hơn.

## 5. Dự án chạy được: một collection, ba cơ chế order

Ví dụ sau dùng **JDK 17+**, **Spring Framework 6.2.19**, không cần Spring Boot hoặc web server. Để minh họa đầy đủ, có năm bean kiểu `Step`:

- `bean-method`: `@Order(5)` trên method `@Bean`.
- `annotated`: `@Order(10)` trên class.
- `dynamic`: implement `Ordered`, trả về `20`.
- `jakarta-priority`: `@Priority(30)` trên class.
- `unordered`: không khai báo metadata, xuất hiện cuối.

### 5.1. Cấu trúc tệp

```text
spring-ordering-demo/
├── settings.gradle
├── build.gradle
└── src/
    └── main/
        └── java/
            └── OrderingDemo.java
```

Nội dung `settings.gradle`:

```groovy
rootProject.name = 'spring-ordering-demo'
```

Nội dung `build.gradle`:

```groovy
plugins {
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-context:6.2.19'
    implementation 'jakarta.annotation:jakarta.annotation-api:2.1.1'
}

application {
    mainClass = 'OrderingDemo'
}
```

Dependency Jakarta được khai báo tường minh để Java compile annotation `@Priority`. Spring Context cung cấp container, `@Configuration`, `@Bean`, `@Order`, `Ordered` và cơ chế autowiring.

### 5.2. Mã nguồn Java hoàn chỉnh

`src/main/java/OrderingDemo.java`:

```java
import java.util.Arrays;
import java.util.List;

import jakarta.annotation.Priority;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

public class OrderingDemo {

    interface Step {
        String name();
    }

    static final class PlainStep implements Step {
        private final String name;

        PlainStep(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }
    }

    @Order(10)
    static final class AnnotatedStep implements Step {
        @Override
        public String name() {
            return "annotated";
        }
    }

    static final class DynamicStep implements Step, Ordered {
        @Override
        public int getOrder() {
            return 20;
        }

        @Override
        public String name() {
            return "dynamic";
        }
    }

    @Priority(30)
    static final class JakartaStep implements Step {
        @Override
        public String name() {
            return "jakarta-priority";
        }
    }

    record Inspection(List<Step> steps, Step[] array) {
        List<String> listNames() {
            return steps.stream().map(Step::name).toList();
        }

        List<String> arrayNames() {
            return Arrays.stream(array).map(Step::name).toList();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class Config {

        @Bean
        Step unordered() {
            return new PlainStep("unordered");
        }

        @Bean
        Step dynamic() {
            return new DynamicStep();
        }

        @Bean
        Step annotated() {
            return new AnnotatedStep();
        }

        @Bean
        Step jakartaPriority() {
            return new JakartaStep();
        }

        @Bean
        @Order(5)
        Step orderedFactoryMethod() {
            return new PlainStep("bean-method");
        }

        @Bean
        Inspection inspection(List<Step> steps, Step[] array) {
            return new Inspection(List.copyOf(steps), array.clone());
        }
    }

    public static void main(String[] args) {
        try (var context =
                     new AnnotationConfigApplicationContext(Config.class)) {
            Inspection inspection = context.getBean(Inspection.class);

            System.out.println("list  = " + inspection.listNames());
            System.out.println("array = " + inspection.arrayNames());
        }
    }
}
```

### 5.3. Cách chạy

Tại thư mục `spring-ordering-demo`, sử dụng bản Gradle tương thích với JDK đã chọn:

```bash
gradle run --quiet
```

Nếu dự án có Gradle Wrapper, thay lệnh trên bằng `./gradlew run --quiet` (macOS/Linux) hoặc `.\gradlew.bat run --quiet` (Windows PowerShell).

Stdout do chương trình in:

```text
list  = [bean-method, annotated, dynamic, jakarta-priority, unordered]
array = [bean-method, annotated, dynamic, jakarta-priority, unordered]
```

Đối chiếu giá trị:

| Phần tử | Nguồn order | Giá trị so sánh |
| --- | --- | ---: |
| `bean-method` | `@Order(5)` trên method `@Bean` | 5 |
| `annotated` | `@Order(10)` trên class | 10 |
| `dynamic` | `Ordered#getOrder()` | 20 |
| `jakarta-priority` | `@Priority(30)` trên class | 30 |
| `unordered` | Không có metadata order | `LOWEST_PRECEDENCE` |

Mặc dù method `unordered()` xuất hiện **đầu tiên** trong class `Config`, bean `unordered` được xếp **cuối** `List<Step>` và `Step[]` vì các bean khác có order rõ ràng. Điều này cho thấy thứ tự collection được quyết định bởi metadata, không phải việc developer viết method nào trước trong source.

Bài kiểm tra này cũng chứng minh Spring xem `@Order` trên method `orderedFactoryMethod()` khi resolve collection. Class `PlainStep` của bean đó không có annotation; nếu chỉ nhìn vào class instance, ta sẽ không thấy giá trị `5`.

## 6. Những tương tác dễ bị nhầm

### 6.1. Thứ tự không phải điều kiện lựa chọn

Order trả lời câu hỏi **“bean nào chạy trước?”**; kiểu và qualifier trả lời **“bean nào được tham gia?”**.

Ví dụ (minh họa, không phải một chương trình Java độc lập):

```java
@Autowired
@Qualifier("payment")
private List<Step> paymentSteps;
```

Spring trước tiên lấy các bean `Step` phù hợp với qualifier `payment`, rồi sắp xếp chúng nếu có metadata order. `@Order(1)` không tự đưa một bean sai kiểu hoặc không khớp qualifier vào danh sách. Tương tự, bean có `@Order(100)` vẫn thuộc danh sách nếu phù hợp; nó chỉ đứng sau các bean có giá trị order nhỏ hơn.

### 6.2. `@Primary` không đồng nghĩa “chạy trước”

`@Primary` hỗ trợ phân giải trường hợp cần **một bean** khi có nhiều ứng viên. Nó không xác định thứ tự `List<Step>`. Một bean có `@Primary` và `@Order(100)` vẫn có thể được xếp sau bean `@Order(5)` trong collection.

`@Priority` có thể ảnh hưởng đến lựa chọn một bean tại một số điểm tiêm đơn, nhưng đây là một **ngữ nghĩa bổ sung** của annotation Jakarta; không vì vậy mà `@Order` hay `Ordered` có thể được thay cho `@Primary`/`@Qualifier` trong mọi trường hợp. Việc chọn một bean và sắp xếp nhiều bean là hai bài toán khác nhau.

### 6.3. Thứ tự injection không phải thứ tự tạo singleton

Đây là một nhầm lẫn quan trọng: `@Order(1)` trên bean A **không bắt Spring khởi tạo A trước B** chỉ vì B có `@Order(2)`. Container tạo các singleton dựa vào việc resolve dependencies và quy trình khởi tạo, còn thứ tự đối tượng trong collection được quyết định khi xây dựng giá trị dependency.

Nếu một bean thực sự phụ thuộc vào bean khác để hoạt động, hãy biểu đạt phụ thuộc qua constructor hoặc, khi cần chỉ định thứ tự khởi tạo bằng tên bean, xem `@DependsOn`. Không dùng `@Order` để xây dựng hợp đồng khởi tạo singleton.

```java
@Bean
@DependsOn("configurationLoader")
Step reportStep() {
    return new PlainStep("report");
}
```

Đây là **đoạn minh họa**, chỉ hợp lệ khi context có bean tên `configurationLoader` và các import tương ứng. `@DependsOn` áp dụng cho quan hệ khởi tạo, không quyết định vị trí của `reportStep` trong `List<Step>`.

### 6.4. Thứ tự xử lý configuration class khác thứ tự bean

`@Order` có thể có ý nghĩa khi áp dụng cho configuration class hoặc các extension point đặc biệt của Spring. Tuy nhiên, `@Order(1)` trên `@Configuration AppConfig` không có nghĩa mọi bean của `AppConfig` đều đứng đầu khi tiêm `List<T>`. Muốn sắp thứ tự những bean cụ thể, dùng annotation tại bean class hoặc từng method `@Bean`, hoặc dùng `Ordered` trên instance.

### 6.5. Tự sắp xếp object bằng comparator Spring

Ngoài autowiring, có thể chủ động sắp xếp một `List` **object instance** bằng `AnnotationAwareOrderComparator`:

```java
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

// Đoạn minh họa; các class được định nghĩa trong OrderingDemo.
List<OrderingDemo.Step> steps = new ArrayList<>(List.of(
        new OrderingDemo.DynamicStep(),
        new OrderingDemo.JakartaStep(),
        new OrderingDemo.AnnotatedStep()
));
AnnotationAwareOrderComparator.sort(steps);
```

Với ví dụ này, thứ tự các object là `annotated` (10), `dynamic` (20), `jakarta-priority` (30). Nhưng một comparator chạy trên những **object thuần** không nhất thiết có metadata nguồn từ method `@Bean`. Spring autowiring collection có thể truy xuất bean definition và factory method, nên mới đọc được `@Order(5)` trên method `orderedFactoryMethod()`. Không mặc định rằng `sort(getBeansOfType(...).values())` luôn tái tạo chính xác thứ tự của collection được autowire nếu metadata order chỉ có trên factory method.

## 7. Tình huống thực tế: pipeline xử lý tuần tự

Một pipeline có ba nhóm chức năng:

- `validate`: kiểm tra dữ liệu đầu vào, `@Order(10)`.
- `transform`: chuẩn hóa dữ liệu, `@Order(20)`.
- `notify`: phát sự kiện sau khi xử lý thành công, `@Order(30)`.

Nếu inject `List<Stage>` vào một service, ta có thể thực thi `for (Stage stage : stages) { stage.execute(context); }` và mỗi class stage chỉ chịu trách nhiệm một bước. Khi thêm `enrich` giữa validate và transform, đặt `@Order(15)`, không cần sửa vòng lặp.

Cần nhớ một số giới hạn thiết kế:

- Các bước **cần thứ tự phụ thuộc mạnh** có thể thích hợp hơn với một quy trình điều phối tường minh, state machine hoặc graph dependencies; chỉ dựa vào số order sẽ khó đọc nếu có nhánh rẽ/phụ thuộc phức tạp.
- Order metadata **không tự xử lý lỗi**. Nếu `validate` ném exception, pipeline sẽ dừng hay tiếp tục phụ thuộc vào logic thực thi, không phụ thuộc `@Order`.
- Order metadata **không đồng nghĩa** chạy tuần tự. `List` chỉ chứa các bước theo thứ tự; chính đoạn code duyệt chúng và gọi `execute` mới quyết định việc chạy tuần tự, song song hay bất đồng bộ.
- Khi hai bước thực sự cần thứ tự cố định, cho chúng **hai giá trị riêng**. Kiểm thử danh sách bean đã resolve bằng context thực để phát hiện thay đổi ngoài ý muốn.

## 8. Lỗi phổ biến và cách chẩn đoán

| Hiện tượng | Nguyên nhân thường gặp | Cách xử lý |
| --- | --- | --- |
| List không theo thứ tự mong muốn | Không gắn order lên **bean đích** hoặc method `@Bean` đúng | Kiểm tra class bean, factory method và kết quả autowire |
| Bean có `@Priority(100)` đứng sau `@Order(10)` | Giá trị **10 nhỏ hơn 100** | Hiểu quy tắc số nhỏ trước; đặt giá trị nhất quán |
| Dùng `@Priority` trên method `@Bean` và compiler lỗi | Jakarta `@Priority` không cho phép target method | Đổi thành `@Order` trên method hoặc gắn `@Priority` trên class |
| Cấu hình `@Order` trên `@Configuration` nhưng List không đổi | Order của configuration class không lan xuống bean method | Gắn `@Order` trực tiếp vào bean/method cần sắp |
| Một bean implement `Ordered` nhưng annotation khác số | `getOrder()` có thể được ưu tiên hơn annotation tĩnh | Chọn một nguồn order rõ ràng, hoặc kiểm tra comparator thực tế |
| Thứ tự giữa hai bean bằng nhau thay đổi | Hai bean có cùng order nên không có ràng buộc thứ tự tương đối | Gán order khác nhau nếu phụ thuộc nghiệp vụ |
| Set hoặc Map được duyệt không theo pipeline | Dùng collection không có hợp đồng xử lý theo order | Tiêm `List<Stage>` hoặc mảng khi cần thứ tự |
| Thấy `@Order(1)` nhưng bean vẫn khởi tạo sau bean khác | Lẫn thứ tự collection với quan hệ khởi tạo | Dùng constructor dependency/`@DependsOn` cho khởi tạo |
| Bean primary không nằm đầu List | `@Primary` không phải metadata order | Dùng `@Order`/`Ordered`/`@Priority` cho thứ tự |
| So sánh thủ công khác autowired List | Mất metadata `@Order` của factory method khi chỉ cầm object | Kiểm tra thứ tự tại điểm injection thật hoặc truyền metadata riêng |

Để chẩn đoán, log **tên và thứ tự của các bean trong List được tiêm** ở đúng điểm sử dụng. Không chỉ nhìn thứ tự khai báo method, thứ tự log khởi tạo hoặc key trong `Map<String, T>`.

## 9. Bài tập và câu hỏi kiểm tra

**Bài tập 1 — Thêm một stage.** Thêm class `IntermediateStep` có `@Order(15)` và đăng ký bằng `@Bean` trả về `new IntermediateStep()`. Dự đoán thứ tự đầu ra mục 5:

```text
[bean-method, annotated, intermediate, dynamic, jakarta-priority, unordered]
```

Vì `5 < 10 < 15 < 20 < 30` và bean không order ở cuối.

**Bài tập 2 — Đổi order runtime.** Sửa `DynamicStep#getOrder()` từ `20` sang `2`. Kết quả mới:

```text
[dynamic, bean-method, annotated, jakarta-priority, unordered]
```

Đây là trường hợp `Ordered` cho phép điều chỉnh trên instance mà không phải đổi annotation.

**Bài tập 3 — Cùng một giá trị.** Cho `@Order(10)` cả `AnnotatedStep` lẫn `JakartaStep` (thay `@Priority` ở class kia). Hỏi thứ tự giữa hai bean này có được bảo đảm không? **Không:** order bằng nhau không biểu đạt quan hệ đứng trước/sau giữa chúng.

**Bài tập 4 — Khởi tạo.** Nếu `@Order(1)` của một stage và `@Order(99)` của stage khác, có thể kết luận constructor của stage thứ nhất chạy trước không? **Không:** thứ tự collection injection không quy định thứ tự tạo singleton.

**Bài tập 5 — Phân biệt API.** Một class implement `PriorityOrdered` với `getOrder() = 100`, một class khác chỉ implement `Ordered` với `getOrder() = 1`. Khi so sánh hai object qua `OrderComparator`, class nào trước? **PriorityOrdered** trước, vì nhóm `PriorityOrdered` có quy tắc ưu tiên riêng. Đây là ngoại lệ cần phân biệt với annotation `@Priority` thông thường.

**Bài tập 6 — Kiến trúc.** Một workflow có nhánh phụ thuộc A→B, A→C, B+C→D và các bước có thể chạy song song. Có nên biểu diễn hoàn toàn bằng `@Order` không? **Không lý tưởng**: các số thứ tự chỉ biểu diễn quan hệ trước/sau tuyến tính; một graph các dependency hoặc orchestration tường minh sẽ truyền đạt đúng luồng hơn.

## 10. Những điểm cần nhớ

1. Khi inject nhiều bean cùng kiểu vào **`List<T>` hoặc `T[]`**, Spring có thể áp dụng giá trị thứ tự từ `Ordered`, `@Order`, `@Priority` để sắp xếp phần tử.
2. **Số nhỏ đi trước**; `@Order(5)` đứng trước `@Priority(30)` trong so sánh thông thường.
3. `Ordered` cho giá trị từ instance, `@Order` cho metadata tĩnh trên class hoặc method `@Bean`; `@Priority` là annotation Jakarta thường đặt trên class.
4. `@Priority` và `PriorityOrdered` là **hai cơ chế khác nhau**. `PriorityOrdered` có ưu tiên nhóm riêng so với plain `Ordered` trong `OrderComparator`.
5. Cần phân biệt **lọc bean** (type/qualifier), **sắp thứ tự bean** (`Ordered`/`@Order`/`@Priority`), **chọn một bean** (`@Primary` và các quy tắc resolution), và **thứ tự khởi tạo** (dependency graph/`@DependsOn`).
6. Nếu pipeline cần thứ tự chạy thực sự, dùng `List` cùng các order value rõ ràng và kiểm thử kết quả resolve.

### Tài liệu tham khảo chính thức

- [Spring Framework Reference — Using `@Autowired` (collection ordering)](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired.html).
- [Spring Framework Javadoc — `Ordered`](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/core/Ordered.html).
- [Spring Framework Javadoc — `@Order`](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/core/annotation/Order.html).
- [Spring Framework Javadoc — `AnnotationAwareOrderComparator`](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/core/annotation/AnnotationAwareOrderComparator.html).
- [Spring Framework Javadoc — `OrderComparator`](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/core/OrderComparator.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="flexible-dependencies-003">Lọc tập hợp bằng qualifier và phân biệt với @Primary</a>

<details>
<summary>Xem chi tiết</summary>

# Lọc tập hợp bean bằng `@Qualifier` và phân biệt với `@Primary`

## 1. Mục tiêu và bối cảnh

Khi một interface có nhiều implementation được Spring IoC container quản lý, một dependency có thể cần **một implementation phù hợp** hoặc **cả nhóm implementation phù hợp**. Hai trường hợp này có ý nghĩa khác nhau:

- Dependency **đơn** như `PaymentHandler handler`: Spring cần chọn đúng một bean.
- Dependency **đa phần tử** như `List<PaymentHandler>`, `Set<PaymentHandler>`, `PaymentHandler[]` hoặc `Map<String, PaymentHandler>`: Spring tập hợp các bean phù hợp theo kiểu. Có thể tiếp tục lọc nhóm đó bằng qualifier.
- `@Primary` chỉ diễn đạt **ưu tiên lựa chọn** khi phải chọn một bean từ nhiều ứng viên; nó không diễn đạt rằng bean khác bị loại khỏi các tập hợp.
- `@Qualifier` diễn đạt **tiêu chí lọc** trong tập các bean đã khớp kiểu, không bắt buộc xác định duy nhất một bean.

Bài này tập trung vào **lọc collection bằng qualifier** và so sánh trực tiếp với `@Primary`. Đây là cơ chế dependency resolution trong Spring Framework Core Container; không cần Spring Boot, web controller hay database.

## 2. Cách Spring chọn bean

Giả sử có bốn bean cùng kiểu `Processor`:

| Tên bean | Qualifier khai báo | `@Primary` |
| --- | --- | --- |
| `main` | `general` | Có |
| `fastOne` | `fast` | Không |
| `fastTwo` | `fast` | Không |
| `slow` | `slow` | Không |

Spring sẽ giải quyết những điểm tiêm dependency sau như thế nào?

| Điểm tiêm | Kết quả | Giải thích |
| --- | --- | --- |
| `Processor preferred` | `main` | Cần một bean; `main` là ứng viên primary duy nhất |
| `@Qualifier("slow") Processor selected` | `slow` | Lọc theo kiểu `Processor` và qualifier `slow`, còn đúng một ứng viên |
| `@Qualifier("fast") List<Processor> fast` | `fastOne` và `fastTwo` | Hai bean đều khớp kiểu và qualifier; không chọn một bean duy nhất |
| `List<Processor> all` | Cả bốn bean | Không có qualifier tại điểm tiêm; primary không loại các bean khác |
| `@Qualifier("fast") Map<String, Processor> fastMap` | Hai cặp với khóa `fastOne` và `fastTwo` | Các giá trị được lọc, còn khóa là **bean name** |
| `@Qualifier("fast") Processor oneFast` | Lỗi ambiguity | Hai bean cùng qualifier `fast`, không có một lựa chọn duy nhất |

Có thể hình dung quy trình:

```text
Điểm tiêm + kiểu generic (nếu có)
          |
          v
Xác định bean ứng viên theo kiểu và điều kiện autowire
          |
          v
Nếu có @Qualifier: chỉ giữ bean khớp tiêu chí
          |
          +--> Dependency dạng List/Set/array/Map:
          |    nhận các bean phù hợp (có thể sắp xếp List/array)
          |
          +--> Dependency đơn:
               cần chọn một bean;
               xét @Primary duy nhất (và các quy tắc phân giải khác)
               hoặc báo lỗi nếu vẫn mơ hồ
```

Đây là mô hình để hiểu, không phải mô tả thứ tự từng phương thức nội bộ của Spring. Quy tắc chọn bean đơn còn xét những tín hiệu khác, ví dụ tên bean/tên tham số, `@Priority` và `@Fallback` ở các phiên bản hỗ trợ; không nên dựa vào các quy tắc phụ khi ý định nghiệp vụ đã có qualifier rõ ràng.

## 3. `@Qualifier` với tập hợp: một nhãn có thể thuộc nhiều bean

Ví dụ hệ thống xử lý dữ liệu có nhiều chiến lược tốc độ cao. Cả `fastOne` và `fastTwo` có cùng nhãn `fast`. Đặt qualifier **trên bean** để khai báo thuộc nhóm, và **trên điểm tiêm** để yêu cầu nhóm đó:

```java
@Bean
@Qualifier("fast")
Processor fastOne() {
    return new Processor("fastOne");
}

@Bean
@Qualifier("fast")
Processor fastTwo() {
    return new Processor("fastTwo");
}

// Tại tham số constructor hoặc phương thức @Bean:
@Qualifier("fast") List<Processor> fastProcessors
```

Spring sẽ tiêm **hai phần tử**, vì qualifier không phải một khóa duy nhất. Nếu sau này thêm `fastThree` cũng có qualifier `fast`, collection sẽ tự có thêm bean đó mà không cần sửa constructor.

Những hình thức phổ biến:

- `@Qualifier("fast") List<Processor>`: lấy các bean phù hợp; phù hợp khi cần thứ tự xử lý.
- `@Qualifier("fast") Set<Processor>`: lấy các bean phù hợp theo kiểu tập hợp; đừng dựa vào thứ tự duyệt của `Set` nếu nghiệp vụ cần thứ tự.
- `@Qualifier("fast") Processor[]`: lấy các bean phù hợp ở dạng mảng.
- `@Qualifier("fast") Map<String, Processor>`: lấy các bean phù hợp; **khóa là tên bean** và **giá trị là bean instance**. Đây không phải map từ qualifier sang bean.

Qualifier trước tiên thu hẹp tập các bean **đã khớp kiểu**. Một bean khác kiểu dù trùng qualifier cũng không được thêm vào `List<Processor>`.

### Qualifier so với tên bean

`@Qualifier("fast")` thường là nhãn có ý nghĩa nghiệp vụ, ví dụ `fast`, `regional`, `audit`. Spring cũng có thể dùng **bean name** làm giá trị qualifier mặc định khi phù hợp; ví dụ `@Qualifier("fastOne")` có thể chọn bean tên `fastOne` ngay cả khi không gắn qualifier cùng chuỗi. Tuy nhiên, mã nghiệp vụ bền vững hơn khi dùng nhãn mô tả đặc tính thay vì gắn với tên method tạo bean.

Đối với `@Bean`, một qualifier ghi trên **phương thức tạo bean** là metadata cho bean đó; qualifier trên **tham số được tiêm** là điều kiện lựa chọn. Đặt qualifier chỉ ở một phía mà không có giá trị khớp sẽ không cho kết quả mong đợi.

## 4. `@Primary`: ưu tiên ở điểm tiêm đơn

Đánh dấu một bean là `@Primary` khi có nhiều bean cùng kiểu nhưng muốn một bean mặc định được chọn trong tình huống cần **một** instance:

```java
@Bean
@Primary
@Qualifier("general")
Processor main() {
    return new Processor("main");
}
```

Với các bean ở bảng đầu bài:

- `Processor preferred` không có qualifier sẽ nhận `main` vì đó là primary duy nhất trong các ứng viên của điểm tiêm đơn.
- `@Qualifier("slow") Processor selected` nhận `slow` dù `main` có `@Primary`: bean `main` không đáp ứng qualifier `slow`, nên không được ưu tiên ở đây.
- `List<Processor> all` vẫn chứa `main`, `fastOne`, `fastTwo`, `slow`. `@Primary` không “lọc mất” ba bean còn lại.
- `@Qualifier("fast") List<Processor> fast` vẫn chứa **cả hai** fast bean. Đánh dấu primary trên một bean nào đó không biến `List` thành danh sách một phần tử.

Nếu có **hai bean primary** cùng là ứng viên cho một điểm tiêm đơn, primary tự nó không thể phân giải sự mơ hồ. Nếu chỉ còn **một** bean sau khi qualifier lọc, Spring chọn bean đó; không cần bean ấy primary. Nếu sau khi qualifier lọc còn nhiều bean mà có đúng một primary trong số đó, primary có thể giúp chọn một bean **cho dependency đơn**, nhưng collection vẫn lấy tất cả bean khớp qualifier.

| Tiêu chí | `@Qualifier` | `@Primary` |
| --- | --- | --- |
| Ý nghĩa | Thu hẹp theo nhãn/thuộc tính | Đặt bean mặc định được ưu tiên |
| Tác dụng trên dependency đơn | Có, nhưng vẫn có thể còn nhiều ứng viên | Có, nếu phân giải được primary duy nhất |
| Tác dụng trên collection | **Lọc các phần tử** | **Không lọc phần tử** |
| Có được dùng lại cùng giá trị cho nhiều bean? | Có; rất hữu ích cho nhóm | Nhiều primary cùng kiểu có thể gây ambiguity ở điểm tiêm đơn |
| Khi nào dùng | Chọn một nhóm chiến lược hoặc một bean theo tiêu chí | Chọn implementation mặc định trong tập bean cùng kiểu |

## 5. Ví dụ đầy đủ chạy được với Java 17+ và Spring Framework 6

Tạo một ứng dụng Gradle thuần Spring Context với cấu trúc:

```text
qualifier-collections-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/QualifierCollectionsDemo.java
```

`settings.gradle`:

```groovy
rootProject.name = 'qualifier-collections-demo'
```

`build.gradle`:

```groovy
plugins {
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-context:6.2.19'
}

application {
    mainClass = 'QualifierCollectionsDemo'
}
```

`src/main/java/QualifierCollectionsDemo.java`:

```java
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;

public class QualifierCollectionsDemo {

    record Processor(String id) {
    }

    record Inspector(
            Processor preferred,
            List<Processor> fast,
            Processor slow,
            List<Processor> all,
            Map<String, Processor> fastMap) {
    }

    @Configuration
    static class Config {

        @Bean
        @Primary
        @Qualifier("general")
        @Order(30)
        Processor main() {
            return new Processor("main");
        }

        @Bean
        @Qualifier("fast")
        @Order(20)
        Processor fastOne() {
            return new Processor("fastOne");
        }

        @Bean
        @Qualifier("fast")
        @Order(10)
        Processor fastTwo() {
            return new Processor("fastTwo");
        }

        @Bean
        @Qualifier("slow")
        @Order(40)
        Processor slow() {
            return new Processor("slow");
        }

        @Bean
        Inspector inspector(
                Processor preferred,
                @Qualifier("fast") List<Processor> fast,
                @Qualifier("slow") Processor slow,
                List<Processor> all,
                @Qualifier("fast") Map<String, Processor> fastMap) {
            return new Inspector(preferred, fast, slow, all, fastMap);
        }
    }

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            Inspector result = context.getBean(Inspector.class);

            System.out.println("single = " + result.preferred().id());
            System.out.println("fast = " + names(result.fast()));
            System.out.println("slow = " + result.slow().id());
            System.out.println("all = " + names(result.all()).stream().sorted().toList());
            System.out.println("fastKeys = " + new TreeSet<>(result.fastMap().keySet()));
        }
    }

    private static List<String> names(List<Processor> items) {
        return items.stream().map(Processor::id).toList();
    }
}
```

Chạy từ thư mục chứa `build.gradle` bằng Gradle và JDK 17 trở lên:

```bash
gradle run --quiet
```

Các dòng do `System.out.println` in ra:

```text
single = main
fast = [fastTwo, fastOne]
slow = slow
all = [fastOne, fastTwo, main, slow]
fastKeys = [fastOne, fastTwo]
```

**Giải thích từng dòng:**

1. `single = main`: tham số `Processor preferred` nhận primary. Tên tham số `preferred` không trùng tên bean `main` nên kết quả thể hiện chính sách primary.
2. `fast = [fastTwo, fastOne]`: qualifier `fast` nhận đúng hai bean; `@Order(10)` trên `fastTwo` đứng trước `@Order(20)` trên `fastOne`. `@Order` định thứ tự khi đưa bean vào danh sách, không biến chúng thành bean primary.
3. `slow = slow`: chỉ bean `slow` khớp `@Qualifier("slow")`, bất kể `main` có `@Primary`.
4. `all = [...]`: không qualifier nên nhận cả bốn bean; mã minh họa sắp xếp danh sách **theo tên** trước khi in để stdout ổn định, không suy ra thứ tự mặc định của Spring từ dòng này.
5. `fastKeys = [fastOne, fastTwo]`: `Map<String, Processor>` lọc theo qualifier, nhưng key vẫn là **tên bean**. `TreeSet` chỉ dùng để hiển thị các key theo thứ tự cố định.

Trong ví dụ, `@Order` đặt trên phương thức `@Bean` là metadata để Spring sắp thứ tự các phần tử được tiêm vào `List`. Khi không khai báo thứ tự, đừng thiết kế logic phụ thuộc vào thứ tự được quan sát tình cờ. Nếu cần thứ tự xử lý bắt buộc, dùng `@Order`/`Ordered` rõ ràng và lựa chọn kiểu collection phù hợp. Thứ tự khởi tạo singleton là vấn đề khác, không do `@Order` quyết định.

## 6. Ba tình huống dễ nhầm và cách kiểm chứng

### 6.1. Nhầm `@Primary` với phép lọc

**Kỳ vọng sai:** Gắn `@Primary` cho `main` thì `List<Processor>` chỉ còn `main`.

**Hành vi đúng:** `List<Processor>` chứa tất cả bean khớp kiểu. `@Primary` áp dụng để giải quyết dependency đơn; nó không ràng buộc thành viên collection. Muốn chỉ một nhóm, dùng `@Qualifier` tại điểm tiêm.

### 6.2. Cho rằng qualifier phải là duy nhất

**Kỳ vọng sai:** Hai bean cùng `@Qualifier("fast")` là cấu hình không hợp lệ.

**Hành vi đúng:** Cùng qualifier là bình thường khi nhiều bean thuộc một nhóm. `@Qualifier("fast") List<Processor>` nhận cả hai. Chỉ khi yêu cầu `@Qualifier("fast") Processor oneFast` mà không có một lựa chọn duy nhất thì mới có nguy cơ `NoUniqueBeanDefinitionException` (thường được bọc trong lỗi tạo bean ở thời điểm khởi tạo context).

Để thử trên ví dụ trên, tạm thêm tham số sau vào phương thức `inspector`:

```java
@Qualifier("fast") Processor oneFast
```

Khi đó cả `fastOne` lẫn `fastTwo` đều là ứng viên; không có primary trong nhóm `fast` nên context khởi tạo thất bại. Đây là **đoạn thay đổi để thử lỗi**, không phải một phần của chương trình hoàn chỉnh đã chạy ở mục 5.

### 6.3. Cho rằng qualifier thắng primary mà không xét kiểu

**Kỳ vọng sai:** `@Qualifier("slow")` luôn chọn bean mang nhãn `slow`, dù bean ấy không triển khai kiểu cần tiêm.

**Hành vi đúng:** Bean phải đáp ứng **kiểu** và tiêu chí qualifier. Nếu điểm tiêm là `@Qualifier("slow") Processor selected`, chỉ bean kiểu `Processor` với qualifier phù hợp mới là ứng viên. Qualifier không ép Spring đưa một bean sai kiểu vào dependency.

## 7. Quy tắc thực hành khi mở rộng hệ thống

### Tiêm vào constructor

Ưu tiên dependency injection qua constructor khi đối tượng phụ thuộc là bắt buộc. Đặt `@Qualifier` trực tiếp trên tham số cần lọc để ý nghĩa hiển thị tại điểm sử dụng:

```java
// Minh họa constructor trong một bean do Spring quản lý.
final class Pipeline {
    private final List<Processor> fastProcessors;

    Pipeline(@Qualifier("fast") List<Processor> fastProcessors) {
        this.fastProcessors = List.copyOf(fastProcessors);
    }
}
```

Đây là đoạn minh họa ngắn, dùng kiểu `Processor` của ứng dụng hoặc khai báo lại kiểu tương ứng trong dự án thực tế. Để Spring tạo `Pipeline`, cần đăng ký lớp đó làm bean qua `@Component`/`@Bean`. Với một constructor duy nhất, Spring không đòi `@Autowired` trên constructor ấy.

### Thiết kế nhãn có ý nghĩa

Qualifier nên nói về **khả năng hay vai trò**, chẳng hạn `fast`, `audit`, `offline`. Tránh dùng nhãn chỉ để mã hóa tên implementation, vì việc đổi implementation có thể làm lộ chi tiết kỹ thuật vào các constructor. Nếu không muốn lặp chuỗi dễ gõ sai, có thể định nghĩa **custom qualifier annotation** (meta-annotated bằng `@Qualifier`) để gắn đặc tính nhóm ở điểm khai báo và điểm tiêm.

### Phân biệt nhóm bean với một bean kiểu collection

`@Qualifier("fast") List<Processor>` trong ví dụ nghĩa là **gộp các bean `Processor`** khớp nhãn `fast`. Đây là trường hợp thông dụng của multi-element injection. Nó cần được phân biệt với một bean có **chính kiểu `List<Processor>`** được tạo bằng `@Bean`. Đối với nhu cầu chọn một collection bean được định nghĩa sẵn, hãy khai báo kiểu generic đủ rõ, cân nhắc tiêm theo tên bằng `@Resource` khi phù hợp và kiểm tra độ phân giải thực tế của container; không áp dụng máy móc quy tắc “lọc từng Processor” cho mọi trường hợp bean kiểu collection.

### Điều kiện không có bean phù hợp

- `@Autowired` mặc định yêu cầu dependency thỏa mãn; với `List`/mảng/`Map` được autowire, mặc định cần có ít nhất một bean phần tử phù hợp. Nếu qualifier lọc ra không bean nào, quá trình tạo bean có thể thất bại với lỗi dependency không thỏa mãn.
- `@Autowired(required = false)` có thể làm một điểm tiêm field/method trở thành không bắt buộc; không nên suy ra rằng Spring sẽ tự đưa một `List.of()` vào mọi constructor.
- Nếu nghiệp vụ cho phép không có phần tử, cân nhắc thiết kế mặc định rỗng một cách chủ động hoặc dùng `ObjectProvider<Processor>` và truy vấn/stream có điều kiện. Cần làm rõ chính sách khi tập hợp trống trong thiết kế API.

### Khi nào cần thứ tự?

Nếu một nhóm handler được thực thi tuần tự, thứ tự có thể là nghiệp vụ quan trọng. Với `List<Handler>`, dùng `@Order` hoặc `Ordered` và kiểm thử thứ tự thật. Với `Set`/`Map`, không giả định rằng ý nghĩa nghiệp vụ được bảo đảm nhờ thứ tự lặp. `@Primary` không phải cơ chế sắp thứ tự các handler.

### Khi nào dùng `@Primary`?

Dùng `@Primary` nếu có một adapter mặc định và các adapter khác dùng cho trường hợp cụ thể. Ví dụ `@Primary` trên adapter bộ nhớ đệm mặc định làm `CacheAdapter` đơn không bị ambiguity, còn `@Qualifier("audit") List<CacheAdapter>` vẫn lấy nhóm audit độc lập. Nếu các hành vi đồng cấp đều cần dùng, một nhóm có qualifier rõ ràng thường hợp lý hơn việc cố chọn một primary.

## 8. Các lỗi thường gặp và cách sửa

| Triệu chứng | Nguyên nhân khả dĩ | Cách xử lý |
| --- | --- | --- |
| `NoUniqueBeanDefinitionException` ở dependency đơn | Có nhiều bean cùng kiểu và sau khi lọc vẫn mơ hồ | Thêm qualifier đủ cụ thể; hoặc khai báo một primary mặc định nếu đúng ngữ nghĩa |
| `NoSuchBeanDefinitionException` / `UnsatisfiedDependencyException` | Không có bean khớp kiểu và qualifier tại điểm tiêm bắt buộc | Kiểm tra đăng ký bean, kiểu return `@Bean`, qualifier ở cả hai phía, package scan |
| `@Qualifier("fast") List<...>` có ít phần tử hơn dự kiến | Bean thiếu qualifier, không khớp generic hoặc không phải autowire candidate | Kiểm tra kiểu phần tử/generic và metadata qualifier của từng bean |
| Map có key khác chuỗi qualifier | Key của `Map<String, T>` là **bean name** | Dùng key để định danh bean; nếu cần map theo nhãn nghiệp vụ, tự chuyển đổi với quy tắc xử lý trùng nhãn |
| Danh sách chạy sai thứ tự | Không khai báo `@Order`/`Ordered` hoặc dựa vào thứ tự ngoài hợp đồng | Khai báo thứ tự xử lý rõ ràng, xác nhận bằng test |
| Gắn nhiều `@Primary` nhưng dependency đơn vẫn lỗi | Primary không còn duy nhất trong tập ứng viên | Chỉ định một default rõ ràng hoặc dùng qualifier để phân biệt |
| Trộn nhầm bean `List<T>` với tập các bean `T` | Hai cấu hình collection khác nhau | Xác định cụ thể muốn lấy collection bean hay gom nhiều bean phần tử |

Không nên giải quyết lỗi bằng cách đổi tên tham số ngẫu nhiên cho khớp tên bean. Spring có cơ chế tên tham số/bean name khi phân giải dependency đơn, nhưng mục đích nhóm nên được biểu đạt bằng qualifier để code ổn định khi refactor.

## 9. Bài tập tự kiểm tra

1. Thêm bean `fastThree` có `@Qualifier("fast")` vào `Config`, không gắn primary. Dự đoán `fast`, `all` và `fastKeys` thay đổi ra sao. Kết quả đúng: nhóm `fast` tăng lên ba, `all` tăng lên năm, `Map` có thêm key `fastThree`; `preferred` vẫn là `main`.
2. Đặt `@Primary` lên cả `main` và `slow`. Dự đoán tiêm `Processor preferred`. Nếu cả hai vẫn là ứng viên và không có quy tắc phân giải khác, lỗi ambiguity xuất hiện; nhóm `fast` không vì thế thay đổi.
3. Giữ `@Primary` trên `main`, thêm `@Qualifier("fast")` vào `main` (bỏ `general`). `@Qualifier("fast") List<Processor>` sẽ có bao nhiêu bean? Đáp án: ba (`main`, `fastOne`, `fastTwo`). Primary vẫn **không loại** hai bean fast còn lại.
4. Vì sao `@Qualifier("fast") Processor selected` có thể lỗi trong khi `@Qualifier("fast") List<Processor> selected` chạy được? Vì điểm tiêm đơn phải xác định **một** bean, còn collection cố ý thu thập **nhiều** bean.
5. Có nên dùng `@Primary` để sắp xếp một pipeline nhiều handler? Không; dùng `@Order`/`Ordered` và biểu đạt luồng thực thi minh bạch.

## 10. Ghi nhớ và tài liệu gốc

- **Qualifier là bộ lọc**, nên một giá trị qualifier có thể gắn trên nhiều bean và chọn cả một nhóm.
- **Primary là ưu tiên chọn một bean**; nó không giới hạn số phần tử của `List`, `Set`, mảng hay `Map`.
- `@Qualifier` trên collection và `@Order` giải quyết hai nhu cầu độc lập: **ai được tham gia** và **thứ tự tham gia**.
- Cần phân biệt collection gồm các bean cùng kiểu với một bean mà bản thân nó có kiểu collection.

Tài liệu chính thức: [Spring — Using `@Autowired`](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired.html), [Spring — Fine-tuning with Qualifiers](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired-qualifiers.html), [Spring — `@Primary` và `@Fallback`](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired-primary.html), [Spring — Using Generics as Autowiring Qualifiers](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/generics-as-qualifiers.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="flexible-dependencies-004">Biểu diễn phụ thuộc tùy chọn bằng Optional</a>

<details>
<summary>Xem chi tiết</summary>

# Biểu diễn phụ thuộc tùy chọn bằng Optional trong Spring Core Container

## Mục tiêu

Một class có thể **cần** repository để hoàn thành nghiệp vụ chính, nhưng chỉ **muốn dùng** audit logger, bộ gửi thông báo hoặc adapter tích hợp bên ngoài khi các bean đó được đăng ký. Loại phụ thuộc thứ hai là *phụ thuộc tùy chọn* (*optional dependency*).

Trong Spring Core Container, `java.util.Optional<T>` là một cách khai báo rõ ràng rằng **bean `T` có thể vắng mặt**. Khi Spring giải quyết injection point có kiểu `Optional<T>`:

- Nếu tìm được một bean `T` thích hợp, Spring đưa vào `Optional.of(bean)` (về mặt ngữ nghĩa là giá trị **có mặt**).
- Nếu không có bean `T` thích hợp, Spring đưa vào `Optional.empty()` thay vì báo lỗi thiếu dependency.
- Nếu có nhiều bean `T` cạnh tranh mà Spring không thể chọn duy nhất một bean, `Optional` **không làm lỗi nhập nhằng biến mất**: vẫn phải giải quyết bằng qualifier, primary hoặc thiết kế lại kiểu phụ thuộc.
- Nếu bean ứng viên bị lỗi khi khởi tạo, `Optional` **không tự động nuốt lỗi đó**.

Sau bài này, bạn sẽ biết cách tiêm `Optional<T>` bằng constructor, phương thức `@Bean` và setter; đọc giá trị an toàn; phân biệt "vắng mặt" với "nhập nhằng"; chọn giải pháp phù hợp cho tính năng tùy chọn.

## 1. `Optional<T>` nói lên điều gì?

`Optional<T>` là một container Java biểu diễn một trong hai trạng thái:

| Trạng thái | Kiểm tra | Diễn giải trong dependency injection |
| --- | --- | --- |
| `Optional.empty()` | `isEmpty() == true` | Spring không tìm thấy bean `T` đáp ứng injection point đó |
| `Optional.of(value)` | `isPresent() == true` | Spring đã phân giải được bean `T` phù hợp |

Ví dụ ý tưởng:

```java
// Đoạn minh họa, bên trong class do Spring quản lý.
final class CheckoutService {
    private final Optional<AuditSink> auditSink;

    CheckoutService(Optional<AuditSink> auditSink) {
        this.auditSink = auditSink;
    }

    void checkout(String orderId) {
        auditSink.ifPresent(sink -> sink.record(orderId));
        // Các bước checkout bắt buộc vẫn được thực hiện.
    }
}
```

`AuditSink` trong ví dụ là interface ứng dụng tự định nghĩa, không phải API có sẵn của Spring. Với **constructor duy nhất**, Spring có thể tự dùng constructor đó mà không cần `@Autowired`. Khi không có bean `AuditSink`, trường `auditSink` nhận `Optional.empty()` và luồng checkout vẫn tiếp tục.

Điểm chính: `Optional<AuditSink>` biểu diễn **không bắt buộc có bean AuditSink**. Nó **không** có nghĩa là bean tùy chọn được tự sinh, khởi tạo trễ, có thể tồn tại nhiều bản sao, hay sẽ tự xuất hiện trong container về sau.

## 2. Spring phân giải `Optional<T>` như thế nào?

Giả sử Spring cần tạo một bean tiêu thụ, trong constructor có tham số `Optional<AuditSink>`:

1. Container đọc kiểu generic bên trong `Optional` để xác định dependency thực tế là `AuditSink`.
2. Spring tìm các bean có kiểu tương thích và thỏa điều kiện autowire tại injection point (ví dụ `@Qualifier`, generic, bean definition và metadata liên quan).
3. Nếu kết quả phân giải ra **một ứng viên hợp lệ**, Spring trao cho constructor một `Optional` chứa bean.
4. Nếu **không có ứng viên**, Spring trao cho constructor `Optional.empty()`; đây là tình huống được dự kiến trước.
5. Nếu **có nhiều ứng viên** mà cơ chế chọn bean vẫn không xác định được một bean duy nhất, quá trình tạo bean có thể thất bại vì nhập nhằng.

`Optional<T>` chỉ thay đổi cách xử lý **thiếu dependency**, không thay thế cơ chế lựa chọn bean. Chẳng hạn, nếu có hai bean cùng triển khai `AuditSink`, Spring có thể chọn một bean được đánh dấu `@Primary`, hoặc bạn có thể dùng `@Qualifier("stdoutAuditSink")` tại tham số `Optional<AuditSink>`. Nếu không có quy tắc phân giải, Spring không tự cho rằng đó là `Optional.empty()`.

**Quan hệ với `null`:** Khi Spring thực hiện injection đúng cách, thứ bạn nhận là **một đối tượng `Optional`**, không phải `null` đại diện cho thiếu dependency. Nếu tự gọi constructor bằng `new CheckoutService(null)` ngoài Spring, bạn đã vi phạm hợp đồng của class; `Optional` không ngăn mã người gọi truyền `null` vào.

## 3. Ví dụ hoàn chỉnh: tính năng audit và email có thể bật/tắt bằng sự hiện diện của bean

Ta xây dựng một ứng dụng Spring Framework thuần (không dùng Spring Boot), chạy hai container riêng:

- Container thứ nhất chỉ chứa cấu hình chung: có `smsNotifier`, nhưng **không** có `AuditSink` và không có `Notifier` mang qualifier `email`.
- Container thứ hai chứa cấu hình chung **và** cấu hình tích hợp: đăng ký thêm bean audit và bean email.

Nhờ đó, ta thấy một `Optional<T>` rỗng khi thiếu bean, và có giá trị khi bean phù hợp xuất hiện. Tình huống email cũng chứng minh rằng **có bean đúng kiểu vẫn chưa đủ** nếu injection point có qualifier khác.

### 3.1. Tạo dự án

Cấu trúc:

```text
spring-optional-di/
├── pom.xml
└── src/main/java/demo/OptionalInjectionDemo.java
```

`pom.xml` (Java 17+, Spring Framework 6.2.19):

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-optional-di</artifactId>
    <version>1.0-SNAPSHOT</version>

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
                <version>3.13.0</version>
                <artifactId>maven-compiler-plugin</artifactId>
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

### 3.2. Mã Java chạy độc lập

`src/main/java/demo/OptionalInjectionDemo.java`:

```java
package demo;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public class OptionalInjectionDemo {

    interface AuditSink {
        void record(String event);
    }

    interface Notifier {
        String channel();
    }

    static final class CheckoutService {
        private final Optional<AuditSink> auditSink;
        private final Optional<Notifier> emailNotifier;

        CheckoutService(Optional<AuditSink> auditSink,
                        Optional<Notifier> emailNotifier) {
            this.auditSink = auditSink;
            this.emailNotifier = emailNotifier;
        }

        void checkout() {
            System.out.println("audit=" +
                    (auditSink.isPresent() ? "present" : "absent"));

            auditSink.ifPresent(sink -> sink.record("order-42"));

            String selectedChannel = emailNotifier
                    .map(Notifier::channel)
                    .orElse("disabled");
            System.out.println("email=" + selectedChannel);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CommonConfig {

        @Bean
        Notifier smsNotifier() {
            return () -> "sms";
        }

        @Bean
        CheckoutService checkoutService(
                Optional<AuditSink> auditSink,
                @Qualifier("email") Optional<Notifier> emailNotifier) {
            return new CheckoutService(auditSink, emailNotifier);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class EnabledConfig {

        @Bean
        AuditSink stdoutAuditSink() {
            return event -> System.out.println("AUDIT: " + event);
        }

        @Bean
        @Qualifier("email")
        Notifier emailNotifier() {
            return () -> "email";
        }
    }

    public static void main(String[] args) {
        try (var context =
                     new AnnotationConfigApplicationContext(CommonConfig.class)) {
            System.out.println("without optional beans:");
            context.getBean(CheckoutService.class).checkout();
        }

        try (var context = new AnnotationConfigApplicationContext(
                CommonConfig.class, EnabledConfig.class)) {
            System.out.println("with optional beans:");
            context.getBean(CheckoutService.class).checkout();
        }
    }
}
```

Chạy trong thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.OptionalInjectionDemo
```

**Standard output do chương trình in** (không tính thông báo riêng của Maven/hệ thống logging):

```text
without optional beans:
audit=absent
email=disabled
with optional beans:
audit=present
AUDIT: order-42
email=email
```

Giải thích:

- Khi chỉ dùng `CommonConfig`, Spring vẫn tạo `CheckoutService` mặc dù thiếu `AuditSink` và thiếu bean `Notifier` có qualifier `email`. Cả hai tham số `Optional` đều rỗng.
- `smsNotifier` là bean `Notifier` thật, nhưng **không thỏa qualifier `email`**. Nó không bị chọn làm email và việc thiếu email không gây lỗi.
- Khi thêm `EnabledConfig`, `stdoutAuditSink` và `emailNotifier` được đăng ký; hai tham số nhận `Optional` có giá trị.
- `checkout()` sử dụng `ifPresent` cho audit, `map(...).orElse(...)` cho email. Luồng chính không cần kiểm tra `null` và không gọi `get()` khi Optional rỗng.
- Mỗi `AnnotationConfigApplicationContext` là một container riêng. Việc đăng ký thêm bean ở ví dụ thứ hai là **khởi tạo container khác**, không phải thay đổi động một container đang chạy.

### 3.3. Đặt `Optional<T>` trên constructor component

Ở dự án dùng component scan, bạn có thể giữ hợp đồng trực tiếp trong constructor:

```java
// Minh họa: interface AuditSink thuộc ứng dụng;
// class phải nằm trong phạm vi component scan.
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class PurchaseProcessor {
    private final Optional<AuditSink> auditSink;

    PurchaseProcessor(Optional<AuditSink> auditSink) {
        this.auditSink = auditSink;
    }

    void handle(String event) {
        auditSink.ifPresent(sink -> sink.record(event));
    }
}
```

Đây là **đoạn minh họa cần đặt vào dự án có AuditSink**, không phải chương trình độc lập. Constructor injection giúp field là `final`, dependency rõ ràng, và dễ tạo instance giả lập trong unit test bằng `Optional.empty()` hay `Optional.of(fake)`.

Không cần đặt `@Autowired` nếu class chỉ có một constructor. Khi class có nhiều constructor, Spring có các quy tắc chọn constructor riêng; `Optional<T>` không tự quyết định Spring sẽ chọn constructor nào.

## 4. Những vị trí tiêm `Optional<T>` thường dùng

### Constructor — ưu tiên cho hợp đồng của class

```java
// Đoạn minh họa trong class Spring-managed:
private final Optional<AuditSink> auditSink;

public MyService(Optional<AuditSink> auditSink) {
    this.auditSink = auditSink;
}
```

Hãy coi đây là một **quyết định thiết kế**: class chấp nhận vận hành khi không có `AuditSink`. Nếu không có dependency đó thì chức năng chính vẫn hoạt động, khai báo `Optional` là hợp lý. Nếu thiếu dependency làm chức năng chính sai, hãy để `AuditSink` là tham số bắt buộc kiểu `AuditSink` thay vì che giấu lỗi cấu hình.

### Tham số của phương thức `@Bean`

```java
@Configuration(proxyBeanMethods = false)
class ServiceConfig {
    @Bean
    MyService myService(Optional<AuditSink> auditSink) {
        return new MyService(auditSink);
    }
}
```

Đoạn này là minh họa cấu hình trong dự án đã định nghĩa `MyService` và `AuditSink`. Spring tự phân giải tham số phương thức factory theo quy tắc dependency injection, nên đây cũng là cách thuận tiện khi bạn không muốn đặt annotation của Spring trong class nghiệp vụ.

### Setter hoặc field dùng `@Autowired`

```java
// Đoạn minh họa trong Spring-managed bean:
private Optional<AuditSink> auditSink = Optional.empty();

@Autowired
void setAuditSink(Optional<AuditSink> auditSink) {
    this.auditSink = auditSink;
}
```

Với injection point kiểu `Optional<T>`, sự thiếu bean `T` **không** khiến setter bắt buộc bị lỗi: Spring có thể truyền `Optional.empty()`. Đây là khác biệt quan trọng với một setter chỉ nhận `AuditSink` và dùng `@Autowired(required = false)`: Spring có thể **bỏ qua hẳn lời gọi setter** nếu không phân giải được dependency của setter đó.

Field injection `@Autowired private Optional<AuditSink> auditSink;` cũng được hỗ trợ, nhưng constructor injection thường dễ bảo trì và dễ test hơn.

## 5. Đọc `Optional` an toàn

Bản thân `Optional` đã đưa ra các phương thức để xử lý đủ hai trường hợp:

| API Java | Khi có giá trị | Khi không có giá trị |
| --- | --- | --- |
| `isPresent()` / `isEmpty()` | Kiểm tra trạng thái | Kiểm tra trạng thái |
| `ifPresent(action)` | Gọi `action` với bean | Không làm gì |
| `ifPresentOrElse(action, emptyAction)` | Gọi `action` | Chạy `emptyAction` |
| `map(transform)` | Biến đổi giá trị và bọc vào Optional mới | Trả `Optional.empty()` |
| `orElse(fallback)` | Trả giá trị có sẵn | Trả fallback |
| `orElseGet(supplier)` | Trả giá trị có sẵn | Mới gọi supplier |
| `orElseThrow(...)` | Trả giá trị có sẵn | Ném exception do ứng dụng quyết định |

Một số mẫu thường dùng:

```java
// Đoạn minh họa bên trong class đã có field auditSink:
// 1. Tác vụ chỉ thực hiện nếu tích hợp có mặt.
auditSink.ifPresent(sink -> sink.record("PAYMENT_SUCCESS"));

// 2. Đường xử lý thay thế nếu thiếu bean.
auditSink.ifPresentOrElse(
        sink -> sink.record("PAYMENT_SUCCESS"),
        () -> System.out.println("Audit adapter not installed")
);

// 3. Trích một giá trị với fallback thuần Java.
String status = auditSink
        .map(sink -> "audit-enabled")
        .orElse("audit-disabled");
```

**Phân biệt `orElse` và `orElseGet`.** `orElse(expensiveFallback())` vẫn tính biểu thức đối số khi gọi phương thức, kể cả Optional đã có giá trị. `orElseGet(() -> expensiveFallback())` chỉ gọi supplier khi Optional rỗng. Nhưng nếu bạn muốn **thực hiện side effect** khi không có bean, `ifPresentOrElse` hoặc `if (optional.isEmpty())` thường diễn đạt ý định rõ hơn.

Tránh `optional.get()` mà không kiểm tra: nếu Optional rỗng, Java ném `NoSuchElementException`. Cũng không dùng `optional.orElse(null)` rồi làm toàn bộ chỗ gọi trở lại kiểu kiểm tra `null` nếu có thể xử lý ngay qua `map`, `ifPresent` hoặc fallback có ý nghĩa.

**Không nên lưu `Optional` trong một field chỉ vì muốn thay thế mọi trường nullable.** Với Spring DI, dùng `Optional<T>` ở **ranh giới tiêm dependency** là cách giao tiếp hợp đồng rõ ràng. Bên trong class, tùy nhu cầu bạn có thể giữ nguyên Optional, hoặc ánh xạ nó sang một chiến lược fallback hợp lệ để giảm phân nhánh; quyết định này không đổi cách Spring chọn bean.

## 6. Kết hợp `Optional<T>` với `@Qualifier` và `@Primary`

### `@Qualifier`: một loại bean nhưng nhiều mục đích

Nếu ứng dụng có nhiều `Notifier` nhưng chỉ một cái phục vụ email:

```java
// Trong một cấu hình Spring; đây là các đoạn minh họa.
@Bean
@Qualifier("email")
Notifier smtpNotifier() {
    return () -> "smtp";
}

@Bean
@Qualifier("sms")
Notifier smsNotifier() {
    return () -> "sms";
}

// Tại injection point (constructor hoặc @Bean factory method):
MyService(@Qualifier("email") Optional<Notifier> notifier) {
    // ...
}
```

`@Qualifier("email")` chỉ **lọc** trong các bean `Notifier`. Khi không có bean thỏa điều kiện, `Optional.empty()`; khi có một bean thỏa điều kiện, Optional chứa bean đó. Nếu có nhiều bean **cùng thỏa qualifier** và không có quy tắc nào xác định được ứng viên duy nhất, lỗi nhập nhằng vẫn có thể xảy ra. Một qualifier không đảm bảo rằng cuối cùng chỉ còn đúng một bean.

### `@Primary`: chọn khi có nhiều bean tương thích

```java
// Minh họa trong @Configuration:
@Bean
@Primary
AuditSink preferredAuditSink() {
    return event -> { /* gửi sang hệ thống audit chính */ };
}

@Bean
AuditSink secondaryAuditSink() {
    return event -> { /* gửi sang hệ thống khác */ };
}
```

Với injection point `Optional<AuditSink>` không có qualifier, `@Primary` giúp Spring chọn một bean ưu tiên trong tình huống nhiều ứng viên phù hợp. Nếu có **hai primary cùng cạnh tranh** hoặc cấu hình khác tạo ra ứng viên không thể phân biệt, Spring vẫn có thể báo lỗi. Không phải cứ bọc kiểu `T` bằng `Optional<T>` là có quyền lấy "bất kỳ một bean" từ tập ứng viên.

### Generic vẫn phải được khai báo đúng

Kiểu generic bên trong `Optional<T>` là thông tin quan trọng. Khi có `Repository<Customer>` và `Repository<Order>`, injection point `Optional<Repository<Customer>>` có thể dùng generic làm điều kiện phân biệt nếu bean definitions giữ được metadata kiểu này. Tránh raw `Optional` hoặc kiểu khai báo quá rộng khiến Spring không biết chính xác `T` mà bạn muốn.

## 7. `Optional<T>` khác `@Autowired(required=false)`, `@Nullable` và `ObjectProvider<T>`

Các công cụ này cùng giải quyết bài toán thiếu dependency nhưng có hợp đồng khác nhau:

| Cách khai báo | Nếu không có bean `T` | Đặc tính |
| --- | --- | --- |
| `Optional<T>` tại injection point | Nhận `Optional.empty()` | Thể hiện rõ cả trạng thái có và không có giá trị; phù hợp dependency tùy chọn đơn lẻ |
| `@Autowired(required=false)` trên field `T` | Spring không gán field; giữ mặc định | Dễ cần `null`/giá trị mặc định; thuộc tính `required` áp dụng ở cấp injection point |
| `@Autowired(required=false)` trên setter `setT(T)` | Setter có thể bị bỏ qua hoàn toàn | Không gọi setter trong trường hợp không thể phân giải |
| `@Nullable T` ở tham số được Spring hỗ trợ | Nhận `null` khi thiếu bean | Giao tiếp bằng nullable; phải xử lý null an toàn |
| `ObjectProvider<T>` | Nhận provider; gọi `getIfAvailable()` có thể trả null | Truy vấn bean có chủ đích tại thời điểm sử dụng, phù hợp lazy/dynamic lookup |
| `T` bắt buộc (không Optional) | Thường lỗi thiếu dependency khi tạo bean | Đúng cho thành phần không thể hoạt động nếu vắng `T` |

`@Nullable` ở đây là annotation mà Spring nhận biết trên **tham số**, ví dụ `@Nullable AuditSink auditSink`. Trong Spring Framework 6.2, hỗ trợ annotation nullable trên type-use có giới hạn; dùng tham số với annotation được hỗ trợ nếu chọn cách này. `Optional` giữ mô hình Java nhất quán hơn vì bạn không cần xử lý tham số `null`.

### Ví dụ đối chiếu `@Autowired(required=false)`

```java
// Đoạn minh họa trong một Spring-managed bean:
private AuditSink auditSink; // mặc định null

@Autowired(required = false)
void setAuditSink(AuditSink auditSink) {
    this.auditSink = auditSink;
}
```

Khi `AuditSink` không tồn tại, setter có thể **không được gọi**. Nếu class cần một mặc định khác `null`, bạn phải thiết kế giá trị ban đầu hoặc một implementation dự phòng. Với `Optional<AuditSink>`, khi Spring inject, caller nhận một đối tượng để hỏi trực tiếp `isPresent()` hoặc `ifPresent()`.

Đừng đặt `@Autowired(required=false)` lên một constructor một cách máy móc và nghĩ từng tham số của constructor đó sẽ thành tùy chọn giống `Optional<T>`. Quy tắc chọn constructor trong Spring khác với field/setter; cách diễn đạt rõ ý định cho một tham số không bắt buộc là khai báo `Optional<T>` ngay tại tham số.

### Khi nào chọn `ObjectProvider<T>`?

`Optional<T>` được phân giải **khi dependency được tiêm** vào bean tiêu thụ. Nó không đảm bảo tạo bean `T` trễ và không tự truy vấn lại khi registry thay đổi.

`ObjectProvider<T>` cho phép truy vấn lúc cần:

```java
// Đoạn minh họa trong Spring-managed bean:
import org.springframework.beans.factory.ObjectProvider;

final class AuditService {
    private final ObjectProvider<AuditSink> auditProvider;

    AuditService(ObjectProvider<AuditSink> auditProvider) {
        this.auditProvider = auditProvider;
    }

    void record(String event) {
        AuditSink sink = auditProvider.getIfAvailable();
        if (sink != null) {
            sink.record(event);
        }
    }
}
```

`getIfAvailable()` có thể trả `null` khi thiếu bean, nhưng **không** có nghĩa mọi lỗi đều được bỏ qua; việc khởi tạo bean hoặc chọn giữa nhiều ứng viên vẫn phải hợp lệ. Provider cũng có `ifAvailable(Consumer<T>)` để bỏ phần kiểm tra null, `getIfUnique()` cho trường hợp chỉ muốn giá trị khi chọn được ứng viên duy nhất theo ngữ nghĩa API, và `stream()`/`orderedStream()` cho nhu cầu lấy nhiều bean. Đặc biệt với scope `prototype`, gọi provider nhiều lần có thể lấy instance khác nhau, tùy cách đăng ký/scope của bean.

Chọn `ObjectProvider<T>` khi cần truy vấn lặp lại, lazy access, bean theo scope hoặc tích hợp nhiều ứng viên. Chọn `Optional<T>` khi tại thời điểm tạo consumer, bạn chỉ cần biết **có hay không một dependency**.

## 8. Phụ thuộc tùy chọn khác tính năng tùy chọn

Không phải mọi tính năng bật/tắt đều nên được thiết kế bằng sự vắng mặt của bean:

- **Phụ thuộc tùy chọn:** `CheckoutService` vận hành bình thường khi không có `AuditSink`; đây là ví dụ tốt cho `Optional<AuditSink>`.
- **Feature flag:** cấu hình có thể nói `audit.enabled=false` dù bean `AuditSink` vẫn tồn tại. Lúc này trạng thái tính năng và sự hiện diện của bean là **hai khái niệm riêng**.
- **Tích hợp theo điều kiện:** nếu configuration quyết định có tạo `AuditSink` hay không, `Optional<AuditSink>` trở thành cách consumer ứng xử khi bean không được đăng ký. Trong Spring Framework thuần có `@Conditional` và cơ chế đăng ký bean có điều kiện; Spring Boot còn cung cấp nhiều `@ConditionalOn...` như `@ConditionalOnProperty`. Các annotation `@ConditionalOn...` là tính năng của **Spring Boot**, không phải API lõi `spring-context` dùng trong ví dụ Maven ở trên.

Nếu hành vi chính **không được phép hoạt động** khi thiếu bean, tránh khai báo Optional chỉ để application khởi động được. Ví dụ, không nên biến `PaymentGateway` bắt buộc thành `Optional<PaymentGateway>` rồi lặng lẽ bỏ qua thanh toán. Khi đó lỗi thiếu cấu hình nên được phát hiện sớm.

Nếu thiếu tích hợp thì cần fallback, hãy xác định rõ fallback đó:

```java
// Minh họa ý tưởng: có thể chọn implementation fallback thuần Java.
AuditSink selected = auditSink.orElseGet(() -> event -> {
    // Không ghi audit sang hệ thống ngoài; chính sách fallback chủ đích.
});
selected.record("order-42");
```

Trong dự án thực, một `NoOpAuditSink` cũng có thể được đăng ký làm bean có điều kiện nếu muốn các consumer luôn nhận dependency không tùy chọn. Đừng để cả fallback bean và bean thực cùng khớp mà không xác định luật lựa chọn.

## 9. Những hiểu lầm phổ biến

### "`Optional<T>` giúp che mọi lỗi bean"

**Sai.** Nó chỉ biểu diễn sự **vắng mặt hợp lệ của dependency**. Một bean `T` có cấu hình sai, vòng phụ thuộc không được hỗ trợ, constructor ném exception hoặc hai ứng viên không thể phân biệt vẫn có thể làm context khởi động thất bại.

### "Có hai bean `T` thì Spring sẽ trả `Optional.empty()`"

**Sai.** Hai bean khác nhau không có nghĩa là thiếu bean. Spring phải chọn một ứng viên theo cơ chế type matching, qualifier, `@Primary` và metadata tương ứng; nếu không chọn được, lỗi ambiguity là điều cần sửa.

### "`Optional<T>` sẽ tự cập nhật nếu có bean mới"

**Sai.** Giá trị `Optional` nhận qua constructor là kết quả phân giải tại thời điểm tiêm. Nếu nghiệp vụ cần lookup động, `ObjectProvider<T>` hoặc các API container phù hợp diễn đạt đúng ý hơn.

### "`Optional<T>` sẽ tự phát hiện config property của kiểu `T`"

**Sai.** Tìm bean và đọc property là hai cơ chế khác nhau. `Optional<AuditSink>` xem container có bean `AuditSink` phù hợp hay không. `application.properties`/`application.yml` không tự tạo bean `AuditSink` chỉ nhờ có một khóa trong file.

### "Nên dùng `Optional` cho tất cả dependency"

Không hợp lý. Dùng `Optional` khi **thiếu dependency là trường hợp hợp lệ được thiết kế trước**. Dependency bắt buộc nên giữ kiểu `T` trực tiếp để ứng dụng thất bại rõ ràng nếu cấu hình thiếu.

### "`Optional<List<T>>` và `List<T>` là một"

Chúng biểu thị hợp đồng khác nhau. `Optional<List<T>>` nói **cả giá trị danh sách có thể vắng mặt**; `List<T>` nói consumer nhận một danh sách phần tử. Với multi-bean collection injection, Spring có các quy tắc riêng cho tập ứng viên và một số trường hợp cho collection rỗng (đặc biệt ở constructor duy nhất). Không tự bọc collection bằng Optional để giả định Spring luôn lấy được một danh sách các bean hay luôn trả empty khi không có phần tử. Hãy chọn `List<T>` khi cần 0..n implementation và xem xét `ObjectProvider<T>.stream()` nếu cần truy vấn động.

## 10. Chẩn đoán lỗi theo tình huống

| Hiện tượng | Kiểm tra | Cách khắc phục |
| --- | --- | --- |
| `Optional.isEmpty()` dù đã viết implementation | Class đã đăng ký thành bean chưa, component scan có đúng package, điều kiện registration có bật không | Đăng ký bean hoặc chỉnh phạm vi scan/cấu hình điều kiện |
| Có bean đúng interface nhưng Optional vẫn rỗng | Qualifier, generic và trạng thái autowire candidate có khớp không | Sửa qualifier, kiểu generic hoặc metadata đăng ký |
| Context lỗi `NoUniqueBeanDefinitionException` | Có nhiều ứng viên `T` hợp lệ nhưng chưa chọn được một cái | Dùng `@Qualifier` hoặc `@Primary`; cân nhắc multi-bean injection nếu cần nhiều bean |
| Context lỗi `BeanCreationException` | Factory/constructor của bean được chọn đã thất bại | Sửa lỗi tạo bean; `Optional` không phải cơ chế bắt lỗi khởi tạo |
| `NullPointerException` ở biến Optional | Đối tượng được tạo bằng `new` ngoài Spring, hoặc người gọi đã truyền `null` | Đảm bảo consumer do Spring quản lý, test truyền `Optional.empty()` |
| `NoSuchElementException` khi gọi `get()` | Optional rỗng nhưng code không kiểm tra | Dùng `ifPresent`, `map`, `orElseGet` hoặc `orElseThrow` có ý nghĩa |
| `@Autowired(required=false)` setter không chạy | Setter có dependency không phân giải được | Cân nhắc tham số `Optional<T>` hoặc thiết kế mặc định rõ ràng |
| Tưởng Optional cho lazy bean nhưng bean vẫn khởi tạo sớm | `Optional<T>` được resolve lúc inject | Dùng `ObjectProvider<T>` cho lazy lookup có chủ đích |

Cách gỡ lỗi có trình tự: (1) xác định bean `T` có được đăng ký không; (2) kiểm tra kiểu thực tế, generic và component scan; (3) đọc các qualifier và `@Primary`; (4) phân biệt **không có ứng viên** với **có quá nhiều ứng viên**; (5) nếu có ứng viên mà vẫn thất bại, kiểm tra exception gốc trong quá trình tạo bean.

Khi kiểm thử, có thể xây hai context như ví dụ: một không đăng ký optional beans và một có đăng ký. Điều này kiểm chứng cả **đường fallback** và **đường có dependency**. Với consumer tự tạo trong unit test, truyền thẳng `Optional.empty()` hoặc `Optional.of(fake)`; không cần dựng toàn bộ context cho các phép kiểm tra nghiệp vụ thuần Java.

## 11. Bài tập và đáp án định hướng

**Bài 1.** Trong ví dụ chạy độc lập, xóa `@Qualifier("email")` khỏi tham số `Optional<Notifier>` của `checkoutService`. Với `CommonConfig`, điều gì xảy ra?

*Đáp án:* Vì chỉ có bean `smsNotifier` thuộc kiểu `Notifier`, tham số Optional bây giờ có thể chứa `smsNotifier`, và chương trình sẽ in `email=sms`. Điều này cho thấy một dependency "tùy chọn" vẫn phải được **lọc đúng ngữ nghĩa**; bỏ qualifier có thể chọn tích hợp không mong muốn.

**Bài 2.** Giữ thay đổi của Bài 1, nhưng thêm cả `EnabledConfig`. Với hai bean `Notifier` (`smsNotifier` và `emailNotifier`) và không có `@Primary`, hãy dự đoán.

*Đáp án:* Spring cần chọn một `Notifier` cho injection point kiểu `Optional<Notifier>`. Nếu không có thêm quy tắc phân biệt hợp lệ, context có thể thất bại vì nhiều ứng viên. Bọc bằng `Optional` không chuyển tình huống này thành empty.

**Bài 3.** Chuyển `AuditSink` từ tính năng phụ sang **điều kiện bắt buộc** để hoàn tất thanh toán. Nên sửa kiểu constructor thế nào?

*Đáp án:* Nhận trực tiếp `AuditSink` thay vì `Optional<AuditSink>`. Khi không đăng ký bean, Spring báo lỗi khởi tạo consumer, giúp phát hiện việc thiếu dependency bắt buộc.

**Bài 4.** Consumer muốn truy cập một `AuditSink` có scope `prototype` mới ở mỗi lần xử lý. Giữ `Optional<AuditSink>` trên constructor có bảo đảm điều đó không?

*Đáp án:* Không. Optional được phân giải khi inject. Cân nhắc `ObjectProvider<AuditSink>` rồi gọi `getObject()`/`ifAvailable(...)` lúc cần; hiểu rõ chính sách scope và hành vi khi thiếu bean.

**Bài 5.** Viết lại cách lấy channel email mà không gọi `get()` và không tạo fallback tốn kém khi Optional có giá trị.

*Đáp án:*

```java
String channel = emailNotifier
        .map(Notifier::channel)
        .orElseGet(() -> computeFallbackChannel());
```

Đây là đoạn minh họa giả định `computeFallbackChannel()` có sẵn trong class; supplier chỉ chạy khi Optional rỗng.

## Tóm lược kiến thức cần ghi nhớ

- `Optional<T>` là **hợp đồng thiếu bean `T` có thể chấp nhận được**.
- Có ứng viên duy nhất/phân giải được → `Optional` có giá trị; không có ứng viên → `Optional.empty()`.
- Nhiều ứng viên không phân giải được, lỗi khởi tạo bean và cấu hình sai **vẫn là lỗi**.
- Dùng constructor injection để khai báo rõ optional dependency; dùng `@Qualifier` khi cần đúng loại implementation.
- Dùng `ifPresent`, `map`, `orElseGet` thay vì `get()` thiếu kiểm tra hoặc tạo `null` không cần thiết.
- Chọn `ObjectProvider<T>` khi cần tra cứu linh hoạt theo thời điểm/scope; dùng dependency kiểu `T` trực tiếp cho chức năng bắt buộc.

## Tài liệu tham khảo chính thức

- Spring Framework, [Using `@Autowired`](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired.html) — Optional, `required=false`, constructor và factory method injection.
- Spring Framework, [Fine-tuning Annotation-based Autowiring with Qualifiers](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired-qualifiers.html) — cách qualifier thu hẹp ứng viên.
- Spring Framework, [Using `@Primary`](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired-primary.html) — chọn ứng viên ưu tiên.
- Spring Framework, [`ObjectProvider<T>` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/beans/factory/ObjectProvider.html) — truy vấn phụ thuộc linh hoạt.
- Java SE 17, [`Optional<T>` Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Optional.html) — `ifPresent`, `map`, `orElse`, `orElseGet`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="flexible-dependencies-005">Tra cứu trì hoãn bằng ObjectFactory và ObjectProvider</a>

<details>
<summary>Xem chi tiết</summary>

# Tra cứu trì hoãn bằng ObjectFactory và ObjectProvider trong Spring Core Container

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

- Giải thích **tra cứu trì hoãn (on-demand lookup)**: tiêm một đối tượng cung cấp quyền tra cứu, rồi chỉ yêu cầu bean đích tại thời điểm thực sự cần dùng.
- Phân biệt `ObjectFactory<T>` và `ObjectProvider<T>`, chọn đúng API khi dependency bắt buộc, tùy chọn hoặc có nhiều implementation.
- Dự đoán tác động của scope `singleton`, `prototype` và scope theo request đối với kết quả `getObject()`.
- Viết mã Spring theo constructor injection, kiểm soát trường hợp không có bean, nhiều bean và thứ tự các bean.
- Tránh nhầm tra cứu trì hoãn với mặc định khởi tạo lười, tạo mới mỗi lần hoặc quản lý vòng đời tự động.

**Bối cảnh:** Spring IoC Container chịu trách nhiệm tạo bean, xác định dependency và quản lý scope. Khi một singleton cần dùng một đối tượng prototype mới cho mỗi lượt xử lý, hoặc một plugin chỉ có ở một số cấu hình, việc tiêm trực tiếp một bean đích từ lúc khởi tạo thường không diễn đạt được nhu cầu ấy.

## 2. Vấn đề: tiêm bean trực tiếp so với tra cứu khi cần

Cách tiêm trực tiếp:

```java
public class JobService {
    private final JobContext context;

    public JobService(JobContext context) {
        this.context = context;
    }

    public void runJob() {
        context.execute();
    }
}
```

Đây là ví dụ cấu trúc, giả định `JobContext` có phương thức `execute()`. `JobService` nắm một tham chiếu `JobContext` đã được giải quyết khi tạo service. Nếu `JobService` là singleton còn `JobContext` là prototype, tham chiếu ấy **không tự đổi** thành một prototype mới ở mỗi lần gọi `runJob()`.

Thay vào đó:

```java
import org.springframework.beans.factory.ObjectProvider;

public class JobService {
    private final ObjectProvider<JobContext> contexts;

    public JobService(ObjectProvider<JobContext> contexts) {
        this.contexts = contexts;
    }

    public void runJob() {
        JobContext current = contexts.getObject();
        current.execute();
    }
}
```

Spring tiêm một provider có thể tra cứu `JobContext`. Mỗi lời gọi `getObject()` sẽ yêu cầu container giải quyết bean ở **thời điểm gọi**, theo quy tắc scope, qualifier và ứng viên hiện có. Trường hợp bean prototype, mỗi lần có thể nhận đối tượng mới; bean singleton vẫn thường trả cùng đối tượng. Hai đoạn trên chỉ minh họa cách tiêm, chưa phải chương trình chạy độc lập.

### Trình tự cơ chế

1. Spring tạo `JobService` và tiêm `ObjectProvider<JobContext>` (hoặc `ObjectFactory<JobContext>`).
2. Service lưu đối tượng tra cứu, không nhất thiết lưu bean `JobContext`.
3. Khi xử lý một công việc, service gọi `getObject()` hoặc API khác trên provider.
4. Container chọn ứng viên hợp lệ, giải quyết scope và cung cấp bean; lỗi về thiếu/không duy nhất hoặc lỗi khởi tạo có thể xuất hiện **tại lần tra cứu**.

**Cần phân biệt:** Provider trì hoãn việc *giải quyết dependency qua provider*, nhưng không đảm bảo tất cả singleton ứng viên đều chưa được khởi tạo. Các singleton không đánh dấu lazy có thể đã được Spring tạo trong giai đoạn khởi động context.

## 3. Hai interface liên hệ với nhau thế nào?

Cả hai thuộc package `org.springframework.beans.factory`:

```java
@FunctionalInterface
public interface ObjectFactory<T> {
    T getObject();
}

// Quan hệ kiểu; lược bỏ các phương thức khác để tập trung vào ý chính.
public interface ObjectProvider<T> extends ObjectFactory<T> {
    // getIfAvailable(), getIfUnique(), stream(), orderedStream(), ...
}
```

Đây là phác họa quan hệ interface, **không phải mã thay thế source Spring**. `ObjectFactory<T>` là hợp đồng tối giản với một thao tác `getObject()`. `ObjectProvider<T>` mở rộng interface đó bằng tra cứu tùy chọn, tra cứu khi duy nhất, cung cấp tham số tạo bean và duyệt nhiều ứng viên. Vì vậy một `ObjectProvider<T>` cũng dùng được ở nơi chấp nhận `ObjectFactory<T>`.

| Nhu cầu | `ObjectFactory<T>` | `ObjectProvider<T>` |
| --- | --- | --- |
| Lấy một bean khi gọi | `getObject()` | `getObject()` |
| Thiếu bean mà muốn nhận `null` hoặc fallback | Không có API riêng | `getIfAvailable()`, `getIfAvailable(Supplier)` |
| Không có ứng viên duy nhất thì bỏ qua | Không có API riêng | `getIfUnique()`, `getIfUnique(Supplier)` |
| Chỉ xử lý nếu có bean/bean duy nhất | Không có API riêng | `ifAvailable(Consumer)`, `ifUnique(Consumer)` |
| Lấy nhiều bean cùng kiểu | Không có API riêng | `stream()`, `orderedStream()`, `iterator()` |
| Truyền đối số tạo bean tại thời điểm gọi | Không hỗ trợ trực tiếp | `getObject(Object... args)` |

Trong ứng dụng Spring thông thường, **ưu tiên `ObjectProvider<T>` nếu cần optional, nhiều ứng viên, sắp thứ tự hoặc fallback**; `ObjectFactory<T>` đủ khi chỉ cần một hàm lấy bean bắt buộc.

## 4. Chọn API theo hợp đồng của dependency

`ObjectFactory<T>` chỉ có `getObject()`: khi gọi, phải phân giải được một bean phù hợp; thiếu bean hoặc nhiều ứng viên không phân định được đều là lỗi. `ObjectProvider<T>` mở rộng lựa chọn cho đúng nhu cầu của consumer:

| Nhu cầu tại thời điểm gọi | API | Khi không có bean | Khi nhiều ứng viên vẫn mơ hồ |
| --- | --- | --- | --- |
| Bắt buộc lấy một bean | `getObject()` | Ném lỗi thiếu bean | Ném lỗi không duy nhất |
| Thiếu bean là hợp lệ | `getIfAvailable()` / `ifAvailable(...)` | Trả `null` / không chạy callback | Vẫn có thể ném lỗi |
| Chỉ xử lý khi chọn được một bean | `getIfUnique()` / `ifUnique(...)` | Trả `null` / không chạy callback | Trả `null` / không chạy callback |
| Chạy toàn bộ plugin phù hợp | `stream()` / `orderedStream()` | Stream rỗng | Duyệt nhiều bean, không ép chọn một |

`@Qualifier`, `@Primary` và metadata của bean vẫn tham gia phân giải: có nhiều bean nhưng một bean primary hợp lệ thì phép tra cứu đơn vẫn có thể thành công. Các overload nhận `Supplier<T>` chỉ tạo fallback khi đúng điều kiện thiếu/không chọn được của từng API; object tự tạo trong supplier không mặc nhiên được Spring quản lý. Không phương thức nào nuốt lỗi khởi tạo bean. Bài tiếp theo phân tích riêng từng phương thức bằng bốn cấu hình bean và output cụ thể; ở bài này, điểm cần ghi nhớ là **`ObjectFactory` tối giản, còn `ObjectProvider` cho consumer chọn chính sách lookup**.

## 5. Ví dụ hoàn chỉnh chạy được với Spring Framework và Java 17+

Ví dụ dùng **Spring Framework 6.2.19** để có phiên bản phụ thuộc cụ thể, không cần Spring Boot. Ta sẽ quan sát bean prototype được tạo khi gọi, plugin tùy chọn vắng mặt và thứ tự nhiều bean theo `@Order`.

Tạo ba file:

```text
provider-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/Demo.java
```

**`settings.gradle`**

```groovy
rootProject.name = 'provider-demo'
```

**`build.gradle`**

```groovy
plugins {
    id 'java'
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-context:6.2.19'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass = 'Demo'
}
```

**`src/main/java/Demo.java`**

```java
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.core.annotation.Order;

public class Demo {
    interface RequestToken {
        int number();
    }

    static final class Token implements RequestToken {
        private final int number;

        Token(int number) {
            this.number = number;
        }

        @Override
        public int number() {
            return number;
        }
    }

    interface OptionalNotifier {
        void notifyUser(String message);
    }

    interface Formatter {
        String name();
    }

    static final class ConsumerService {
        private final ObjectFactory<RequestToken> tokenFactory;
        private final ObjectProvider<RequestToken> tokenProvider;
        private final ObjectProvider<OptionalNotifier> notifierProvider;
        private final ObjectProvider<Formatter> formatterProvider;

        ConsumerService(
                ObjectFactory<RequestToken> tokenFactory,
                ObjectProvider<RequestToken> tokenProvider,
                ObjectProvider<OptionalNotifier> notifierProvider,
                ObjectProvider<Formatter> formatterProvider) {
            this.tokenFactory = tokenFactory;
            this.tokenProvider = tokenProvider;
            this.notifierProvider = notifierProvider;
            this.formatterProvider = formatterProvider;
        }

        void demonstrate() {
            System.out.println("before-lookup");

            RequestToken first = tokenFactory.getObject();
            RequestToken second = tokenProvider.getObject();

            System.out.println("different=" + (first != second));
            System.out.println("numbers=" + first.number() + "," + second.number());
            System.out.println("notifier=" +
                    (notifierProvider.getIfAvailable() == null ? "absent" : "present"));
            System.out.println("unique-formatter=" +
                    (formatterProvider.getIfUnique() == null ? "none" : "one"));
            System.out.println("formatters=" + formatterProvider.orderedStream()
                    .map(Formatter::name)
                    .collect(Collectors.joining(",")));
        }
    }

    @Configuration
    public static class Config {
        private final AtomicInteger sequence = new AtomicInteger();

        @Bean
        @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
        RequestToken requestToken() {
            int next = sequence.incrementAndGet();
            System.out.println("create-token-" + next);
            return new Token(next);
        }

        @Bean
        @Order(10)
        Formatter plainFormatter() {
            return () -> "plain";
        }

        @Bean
        @Order(1)
        Formatter jsonFormatter() {
            return () -> "json";
        }

        @Bean
        ConsumerService consumerService(
                ObjectFactory<RequestToken> tokenFactory,
                ObjectProvider<RequestToken> tokenProvider,
                ObjectProvider<OptionalNotifier> notifierProvider,
                ObjectProvider<Formatter> formatterProvider) {
            return new ConsumerService(
                    tokenFactory, tokenProvider, notifierProvider, formatterProvider);
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(Config.class)) {
            System.out.println("context-ready");
            context.getBean(ConsumerService.class).demonstrate();
        }
    }
}
```

**Chạy** tại thư mục `provider-demo` với Gradle có sẵn và JDK tương thích toolchain:

```bash
gradle run --quiet
```

Hoặc dùng `./gradlew run --quiet` khi dự án có Gradle Wrapper (trên Windows dùng `gradlew.bat run --quiet`). Chương trình Java được biên dịch qua tác vụ Gradle `compileJava`; không thể chạy `javac Demo.java` đơn lẻ nếu chưa cung cấp Spring JAR lên classpath.

**Các dòng do chương trình in ra (stdout):**

```text
context-ready
before-lookup
create-token-1
create-token-2
different=true
numbers=1,2
notifier=absent
unique-formatter=none
formatters=json,plain
```

**Giải thích từng quan sát:**

1. `requestToken()` tạo **prototype**. Context khởi động nhưng chưa tạo token, vì không ai yêu cầu bean đích trước `demonstrate()`.
2. `tokenFactory.getObject()` và `tokenProvider.getObject()` mỗi lần dẫn tới một lượt tạo prototype, nên `different=true`; counter tăng từ 1 lên 2.
3. Không có `@Bean OptionalNotifier`. Việc tiêm `ObjectProvider<OptionalNotifier>` vẫn thực hiện được, `getIfAvailable()` trả `null`.
4. Có hai `Formatter`, không có ứng viên được chọn là duy nhất, nên `getIfUnique()` trả `null`.
5. `orderedStream()` duyệt cả hai formatter theo thứ tự Spring xác định từ `@Order(1)` rồi `@Order(10)`: `json,plain`. `stream()` thông thường không cam kết thứ tự này.

Bản demo đủ để thực hành cả hai interface và thấy rõ tác động của scope. Các log cấu hình môi trường/Gradle, nếu có, không nằm trong bảng stdout do `Demo` tự in.

## 6. Scope quyết định ý nghĩa “mỗi lần gọi”

| Scope bean đích | Hai lần `provider.getObject()` trong cùng điều kiện | Lưu ý |
| --- | --- | --- |
| `singleton` (mặc định) | Thường **cùng instance** trong một container | Provider không biến singleton thành prototype; non-lazy singleton có thể đã được khởi tạo từ lúc refresh. |
| `prototype` | **Hai instance khác nhau** trong các lần giải quyết thông thường | Mỗi lookup tạo mới; container không quản lý hủy prototype đầy đủ sau khi cấp. |
| `request` | Thường cùng instance trong **cùng HTTP request**; request khác có instance khác | Chỉ tra cứu khi request scope đang hoạt động, trừ khi đã có cơ chế proxy phù hợp. |
| `session` | Thường cùng instance trong một session; session khác có instance khác | Không tự kéo dài/đổi scope của bean bằng provider. |

Ví dụ một singleton gọi `provider.getObject()` hai lần thì có thể nhận cùng singleton. Thêm `@Scope("prototype")` ở **bean đích** mới thay đổi quy tắc tạo instance; không cần đổi scope của consumer.

Với prototype chứa tài nguyên cần đóng (socket, file, client...), phải thiết kế cơ chế kết thúc vòng đời thích hợp; đừng suy luận rằng `getObject()` tự đăng ký hủy tất cả prototype. Nếu cần dùng request-scoped dependency trong singleton, provider là cách lấy đối tượng theo scope tại thời điểm xử lý request, nhưng gọi từ thread không có request context có thể phát sinh `ScopeNotActiveException`.

## 7. Nhiều bean, @Qualifier và @Primary

Giả sử có hai implementation `PaymentGateway`:

```java
@Bean("cardGateway")
PaymentGateway cardGateway() {
    return new CardGateway();
}

@Bean("bankGateway")
PaymentGateway bankGateway() {
    return new BankGateway();
}
```

Đây là đoạn cấu hình minh họa; `PaymentGateway`, `CardGateway` và `BankGateway` là các kiểu nghiệp vụ giả định. Nếu consumer tiêm `ObjectProvider<PaymentGateway>` không có qualifier và container không chọn được ứng viên rõ ràng, gọi `getObject()` / `getIfAvailable()` có thể báo nhiều ứng viên.

**Chỉ định đích ở injection point:**

```java
public PaymentService(
        @org.springframework.beans.factory.annotation.Qualifier("cardGateway")
        ObjectProvider<PaymentGateway> gateways) {
    this.gateways = gateways;
}
```

Với các field và constructor tương ứng trong `PaymentService`, provider này được gắn điều kiện ứng viên `cardGateway`; `getObject()` sẽ tra cứu theo điều kiện đó. Đây là đoạn minh họa constructor, không phải một class hoàn chỉnh để biên dịch riêng.

**Nếu muốn một ứng viên mặc định**, có thể khai báo `@Primary` trên bean phù hợp. Spring có thể ưu tiên primary trong tra cứu đơn; `getIfUnique()` không đơn giản đếm tất cả định nghĩa rồi luôn trả null khi số lượng lớn hơn một. **Nếu muốn dùng tất cả implementation**, gọi `stream()` hoặc `orderedStream()` và tránh tra cứu đơn.

```java
// Đặt trong một bean đã được tiêm ObjectProvider<PaymentGateway>.
gateways.orderedStream().forEach(gateway -> gateway.healthCheck());
```

Trong ví dụ trên, `PaymentGateway` cần khai báo phương thức `healthCheck()`. `orderedStream()` dùng quy tắc thứ tự của Spring như `Ordered`/`@Order`; `stream()` không có cam kết sắp xếp cố định. Việc lấy stream và tiêu thụ stream có thể kích hoạt giải quyết/tạo các bean phù hợp; không dùng stream chỉ để kiểm tra sự tồn tại nếu bạn không muốn khởi tạo tất cả.

## 8. Tiện ích API thường dùng khi viết ứng dụng

### 8.1. Fallback chỉ khi cần

```java
NotificationSender sender =
        senders.getIfAvailable(() -> new ConsoleNotificationSender());
```

`senders` có kiểu `ObjectProvider<NotificationSender>`; hai lớp/interface trong ví dụ là kiểu nghiệp vụ giả định. Nếu có ứng viên hợp lệ, Spring cấp bean đã cấu hình. Chỉ khi không có bean, lambda mới tạo đối tượng mặc định **ngoài container**. Vì vậy fallback tự tạo không mặc nhiên nhận lifecycle, proxy, AOP hay dependency injection như Spring-managed bean.

Nếu yêu cầu là chỉ chạy khi ứng viên duy nhất:

```java
senders.ifUnique(sender -> sender.send("ready"));
```

`NotificationSender` được giả định có `send(String)`. Nếu không có bean hoặc không chọn được duy nhất, action không chạy.

### 8.2. Truyền đối số khi khởi tạo bean

`ObjectProvider<T>` có `getObject(Object... args)` để yêu cầu bean với đối số runtime, thường hữu ích cho prototype có constructor hoặc factory method nhận tham số. Đoạn cấu hình minh họa:

```java
public record ExportTask(String format) {}

@Bean
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
ExportTask exportTask(String format) {
    return new ExportTask(format);
}

// Trong bean consumer đã được tiêm:
ExportTask csv = tasks.getObject("csv");
// tasks có kiểu ObjectProvider<ExportTask>.
```

`getObject("csv")` yêu cầu tạo đối tượng prototype với đối số `format="csv"`. Đây **không phải** phương thức bổ sung của `ObjectFactory<T>` và không nên dùng để truyền tham số tùy tiện vào một singleton đã được tạo. Đối với code hoàn chỉnh, đặt record, factory method và provider injection vào class/configuration hợp lệ như ở ví dụ `Demo`.

### 8.3. Duyệt nhiều bean

```java
List<String> formatterNames = formatterProvider.orderedStream()
        .map(Formatter::name)
        .toList();
```

Snippet dùng `Formatter`/`formatterProvider` từ chương trình `Demo` và Java 17. `orderedStream()` phù hợp cho pipeline plugin/filter có ưu tiên. `stream()` hoặc for-each qua provider phù hợp khi thứ tự không có ý nghĩa nghiệp vụ. Từ Spring Framework 6.2 có thêm một số overload cho lọc candidate; cần kiểm tra Javadoc của đúng phiên bản nếu sử dụng API đó.

## 9. So sánh với những cách tiêm dependency khác

| Cách | Đặc điểm | Khi phù hợp |
| --- | --- | --- |
| Tiêm trực tiếp `T` | Dependency được giải quyết lúc tạo consumer | Dependency bắt buộc, dùng thường xuyên, scope tương thích. |
| `ObjectFactory<T>` | Lấy một đối tượng khi gọi `getObject()` | Cần runtime lookup tối giản, chẳng hạn lấy prototype theo mỗi lần xử lý. |
| `ObjectProvider<T>` | Runtime lookup, optional, unique và nhiều ứng viên | Feature tùy chọn, plugin, prototype, nhiều implementation. |
| `@Lazy T` tại injection point | Spring thường tiêm proxy và giải quyết target khi proxy được sử dụng | Muốn giữ API gọi như đối tượng trực tiếp; khó biểu đạt điều kiện thiếu bean và nhiều ứng viên. |
| `Optional<T>` | Thể hiện optional tại **thời điểm injection** | Chỉ cần biết có/không tại thời điểm khởi tạo consumer, không cần lookup lặp lại. |
| `jakarta.inject.Provider<T>` | API chuẩn `get()` theo JSR-330/Jakarta Inject | Muốn phụ thuộc vào API DI tiêu chuẩn; cần dependency Jakarta Inject tương ứng. |
| `ApplicationContext.getBean(...)` | Tra cứu trực tiếp qua context | Mã hạ tầng thật sự cần thao tác container; không nên rải trong business service. |

**Phân biệt `ObjectFactory<T>` và `FactoryBean<T>`:** `ObjectFactory` là handle tra cứu được đưa cho bên sử dụng; `FactoryBean` là điểm mở rộng để một thành phần trong container **sản xuất** object cho bean definition. Chúng không đồng nghĩa và không nên hoán đổi chỉ vì tên gần giống.

## 10. Những lỗi thường gặp và cách sửa

**Lỗi 1 — Nghĩ provider luôn tạo instance mới.**

Sai: “Gọi `getObject()` hai lần luôn nhận hai đối tượng khác nhau.”

Đúng: kết quả do **scope bean đích** quyết định. Kiểm tra bean có `@Scope("prototype")` hay không; với singleton, việc tái sử dụng instance là hành vi dự kiến.

**Lỗi 2 — Nghĩ `getIfAvailable()` nuốt cả lỗi nhiều bean.**

Khi không có bean, method trả `null`. Nhưng nếu có nhiều ứng viên ngang nhau, lỗi `NoUniqueBeanDefinitionException` vẫn có thể xảy ra. Nếu cần bỏ qua sự mơ hồ, dùng `getIfUnique()`; nếu cần tất cả, dùng `stream()`; nếu phải chọn một, thiết kế qualifier/primary.

**Lỗi 3 — Gọi `getObject()` ngay trong constructor của consumer.**

```java
// Kém hiệu quả nếu mục tiêu là lookup mỗi lần xử lý:
this.worker = workerProvider.getObject();
```

Gọi sớm như vậy thực chất giải quyết và lưu một bean tại lúc dựng consumer. Nếu muốn prototype mới mỗi lượt xử lý, lưu **provider** và gọi `getObject()` trong phương thức xử lý.

**Lỗi 4 — Quên xử lý kết quả `null`.**

```java
// Có thể NPE nếu không có bean:
provider.getIfAvailable().run();

// An toàn hơn:
provider.ifAvailable(service -> service.run());
```

Giả định bean có phương thức `run()`. Nếu không có bean nhưng hành vi là bắt buộc, `getObject()` mới phản ánh đúng hợp đồng.

**Lỗi 5 — Dùng provider thay cho việc thiết kế scope và lifecycle.**

Provider không tạo ra một request context, không giải quyết việc prototype cần đóng tài nguyên, và không tự động làm một đối tượng trở nên thread-safe. Chỉ tra cứu bean trong context/scope hợp lệ và giữ thời gian sử dụng phù hợp.

**Lỗi 6 — Dựa vào thứ tự không được bảo đảm.**

Nếu pipeline có thứ tự quan trọng, sử dụng `orderedStream()` cùng metadata `@Order`/`Ordered`, không dựa vào thứ tự đăng ký bean hay thứ tự ngẫu nhiên của `stream()`.

**Lỗi 7 — Che lỗi cấu hình bằng fallback tùy ý.**

Fallback có thể làm chương trình “chạy được” nhưng thiếu chức năng bắt buộc. Chỉ dùng `getIfAvailable` cho dependency thực sự **optional**; các thành phần cốt lõi nên dùng `getObject()` hoặc tiêm trực tiếp để fail fast.

## 11. Checklist thiết kế và kiểm thử

Trước khi chọn API, tự trả lời bốn câu hỏi:

1. Dependency **bắt buộc hay tùy chọn**? Bắt buộc: `getObject()`; tùy chọn: `getIfAvailable()`/`ifAvailable()`.
2. Có **nhiều implementation** không? Nếu cần tất cả, dùng stream; nếu cần duy nhất, quy định bằng qualifier/primary hoặc dùng `getIfUnique()`.
3. Bean đích có scope gì? Singleton không thay đổi instance theo từng lookup; prototype tạo mới; web scope cần context phù hợp.
4. Có cần **thời điểm truy cập** do consumer kiểm soát không? Nếu không, constructor injection trực tiếp thường đơn giản và rõ ràng hơn.

Các tình huống nên thử trong test:

- Không đăng ký `OptionalNotifier`: consumer vẫn khởi động và `getIfAvailable() == null`.
- Đăng ký một notifier hợp lệ: provider trả notifier; callback `ifAvailable` chạy.
- Đăng ký hai formatter không primary: `getIfUnique() == null`, `getObject()`/`getIfAvailable()` có thể báo không duy nhất.
- Đăng ký `@Primary` trên một formatter: kiểm tra khả năng chọn bean mặc định bằng lookup đơn.
- Gọi lấy `RequestToken` prototype hai lần: hai instance khác nhau; đổi scope thành singleton: cùng instance.
- So sánh `orderedStream()` với khai báo `@Order`; kiểm tra logic dựa trên thứ tự mong đợi.

**Câu hỏi tự luyện:**

1. Vì sao inject `ObjectFactory<PrototypeBean>` vào singleton giúp lấy đối tượng prototype mới trên từng lần dùng?
2. `getIfAvailable()` và `getIfUnique()` khác gì khi có hai ứng viên ngang nhau?
3. Thay `@Scope("prototype")` bằng singleton trong ví dụ `Demo` thì các dòng liên quan đến token thay đổi ra sao?
4. Khi nào `@Lazy` hợp lý hơn, và khi nào `ObjectProvider<T>` diễn đạt ý định rõ hơn?

**Gợi ý đáp án:** (1) Service giữ handle tra cứu; mỗi lượt gọi container áp dụng scope prototype. (2) `getIfAvailable()` có thể ném lỗi không duy nhất; `getIfUnique()` trả `null`. (3) Bean token thường được tạo một lần và hai lookup nhận cùng instance; không còn bảo đảm hai lần tạo và `different` trở thành `false`. (4) `@Lazy` tiện khi chỉ cần proxy để trì hoãn sử dụng một target; provider phù hợp khi consumer phải tự quyết định thời điểm tra cứu, tính optional hoặc nhiều ứng viên.

## 12. Tài liệu chuẩn để đối chiếu

- [Spring Framework — ObjectFactory Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/beans/factory/ObjectFactory.html)
- [Spring Framework — ObjectProvider Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/beans/factory/ObjectProvider.html)
- [Spring Framework — Scoped Beans as Dependencies](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html)
- [Spring Framework — Autowiring Collaborators](https://docs.spring.io/spring-framework/reference/core/beans/autowire.html)
- [Spring Framework — Classpath Scanning and @Lazy injection points](https://docs.spring.io/spring-framework/reference/core/beans/classpath-scanning.html)

**Ghi nhớ:** `ObjectFactory` và `ObjectProvider` cho phép **lookup khi cần**; chính **scope và quy tắc lựa chọn bean** quyết định cái gì được trả về, còn phương thức provider quyết định cách xử lý tình huống thiếu/không duy nhất.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="flexible-dependencies-006">getObject, getIfAvailable và getIfUnique</a>

<details>
<summary>Xem chi tiết</summary>

# Spring ObjectProvider: phân biệt `getObject()`, `getIfAvailable()` và `getIfUnique()`

## 1. Vấn đề: khi một dependency có thể thiếu hoặc có nhiều bean

Khi một class cần sử dụng một service được quản lý bởi Spring IoC Container, cách thông thường là constructor injection:

```java
// Minh họa: PaymentGateway phải có đúng một bean phù hợp.
PaymentService(PaymentGateway gateway) {
    this.gateway = gateway;
}
```

Cách này lý tưởng với dependency **bắt buộc**: khi thiếu bean hoặc có nhiều ứng viên không phân giải được, lỗi sẽ xuất hiện trong lúc tạo đối tượng. Nhưng một số tính năng là tùy chọn, phụ thuộc vào cấu hình, hoặc cần chọn bean tại thời điểm sử dụng. Khi đó, thay vì yêu cầu ngay `PaymentGateway`, ta có thể tiêm:

```java
// Minh họa: constructor của một bean Spring.
PaymentService(ObjectProvider<PaymentGateway> gatewayProvider) {
    this.gatewayProvider = gatewayProvider;
}
```

`ObjectProvider<T>` thuộc package `org.springframework.beans.factory` và là một biến thể của `ObjectFactory<T>`. Nó cho phép **lấy bean theo yêu cầu** và kiểm soát cách ứng xử khi bean không tồn tại hoặc khi có nhiều bean cùng kiểu. Đối tượng provider gắn với Spring `BeanFactory`; mỗi lần gọi phương thức của provider, việc phân giải target bean có thể được thực hiện lại dựa trên trạng thái của factory.

Ba phương thức trọng tâm:

- `getObject()`: **yêu cầu phải lấy được một bean**; thiếu bean hoặc nhiều bean mơ hồ thì báo lỗi.
- `getIfAvailable()`: **cho phép không có bean** và trả `null` nếu không có; nhưng **không bỏ qua lỗi nhiều bean mơ hồ**.
- `getIfUnique()`: **chỉ trả bean khi có ứng viên thắng duy nhất**; trả `null` khi không có bean hoặc không thể chọn duy nhất.

**Điểm dễ nhầm nhất:** Tên `getIfAvailable` không có nghĩa là “cứ có bean bất kỳ thì trả một bean ngẫu nhiên”; tên `getIfUnique` cũng không có nghĩa “chỉ được có đúng một bean definition trong cả container”. Cả hai vẫn tuân theo cơ chế chọn ứng viên của Spring, trong đó một bean có `@Primary` phù hợp có thể được chọn làm kết quả duy nhất từ nhiều bean cùng kiểu.

## 2. Bảng hành vi cần nhớ

Giả sử `ObjectProvider<PaymentGateway> provider` đang yêu cầu một bean kiểu `PaymentGateway` và không có qualifier nào thu hẹp lựa chọn.

| Trạng thái ứng viên | `getObject()` | `getIfAvailable()` | `getIfUnique()` |
| --- | --- | --- | --- |
| Không có bean phù hợp | Ném `NoSuchBeanDefinitionException` | `null` | `null` |
| Có đúng một bean phù hợp | Trả bean đó | Trả bean đó | Trả bean đó |
| Có hai bean, không chọn được một ứng viên thắng | Ném `NoUniqueBeanDefinitionException` | Ném `NoUniqueBeanDefinitionException` | `null` |
| Có hai bean, đúng một bean `@Primary` | Trả bean primary | Trả bean primary | Trả bean primary |
| Có nhiều bean, nhưng qualifier chỉ giữ một ứng viên | Trả bean phù hợp | Trả bean phù hợp | Trả bean phù hợp |

`NoUniqueBeanDefinitionException` là một dạng `NoSuchBeanDefinitionException` trong hệ phân cấp exception của Spring; khi muốn phân biệt hai trường hợp, hãy `catch (NoUniqueBeanDefinitionException)` **trước** `catch (NoSuchBeanDefinitionException)`.

Bảng này mô tả kết quả khi bản thân việc tạo bean diễn ra bình thường. Nếu factory gọi constructor/factory method và xảy ra lỗi khởi tạo bean, các phương thức provider vẫn có thể ném `BeansException` liên quan. `getIfAvailable()` và `getIfUnique()` **không phải** cơ chế nuốt mọi lỗi bean creation.

### “Unique” nghĩa là kết quả chọn được một ứng viên

Spring sử dụng chính sách lựa chọn của container để phân giải tính duy nhất. Ngoài trường hợp thực sự chỉ có một bean, các tín hiệu như:

- `@Primary`: một ứng viên được đánh dấu primary rõ ràng.
- `@Fallback` từ Spring Framework 6.2: khi có một bean không phải fallback trong số các ứng viên fallback, bean bình thường có thể được chọn.
- Metadata default-candidate và các quy tắc phân giải ứng viên khác của container.

có thể giúp xác định một ứng viên thắng. Vì thế, **không nên tự kiểm tra `provider.stream().count() == 1` rồi cho rằng kết quả đó luôn tương đương `getIfUnique()`**: đếm tất cả bean không mô phỏng toàn bộ chính sách phân giải singular dependency.

## 3. `getObject()`: lấy một bean bắt buộc

Cú pháp:

```java
PaymentGateway gateway = provider.getObject();
```

Hành vi:

1. Spring tìm các bean thỏa kiểu generic `PaymentGateway`, qualifier và điều kiện ứng viên liên quan.
2. Nếu có một ứng viên phù hợp, hoặc có một ứng viên thắng sau phân giải, provider trả object đó.
3. Nếu không có ứng viên, ném `NoSuchBeanDefinitionException`.
4. Nếu còn nhiều ứng viên mà không có một lựa chọn duy nhất, ném `NoUniqueBeanDefinitionException`.
5. Nếu tạo bean thất bại, lỗi khởi tạo bean vẫn được phản ánh cho caller.

`getObject()` phù hợp khi **tại thời điểm thực hiện tính năng, bắt buộc phải có dependency**. Chẳng hạn chức năng thanh toán chính thức chỉ hoạt động nếu gateway đã được cấu hình đúng; báo lỗi là chính sách đúng, thay vì âm thầm chạy một gateway mặc định không được cấp phép.

Có thể bắt lỗi để thêm thông tin nghiệp vụ, nhưng cần phân biệt lỗi thiếu cấu hình với lỗi lập trình:

```java
// Minh họa trong method có biến provider kiểu ObjectProvider<PaymentGateway>.
try {
    PaymentGateway gateway = provider.getObject();
    gateway.pay();
} catch (NoUniqueBeanDefinitionException ex) {
    throw new IllegalStateException("Có nhiều payment gateway chưa được phân giải", ex);
} catch (NoSuchBeanDefinitionException ex) {
    throw new IllegalStateException("Chưa cấu hình payment gateway", ex);
}
```

Đoạn này chỉ minh họa API và chính sách lỗi; method `pay()` thuộc interface nghiệp vụ giả định, được triển khai trong dự án thực tế. Không nên bắt `Exception` chung rồi trả giá trị giả, vì làm mất thông tin lỗi cấu hình của hệ thống.

### `getObject(Object... args)`

Ngoài phiên bản không tham số, `getObject(Object... args)` có thể truyền **đối số tạo bean** cho factory trong những trường hợp phù hợp, đặc biệt khi lấy bean scope prototype qua constructor/factory method nhận tham số. Đây **không phải** API truyền qualifier, không phải cách chọn bean bằng tên, và không nên dùng để thay đổi dữ liệu khởi tạo của singleton đã tồn tại. Nếu dùng overload này, hãy kiểm tra cách tạo bean và scope cụ thể.

## 4. `getIfAvailable()`: cho phép dependency vắng mặt

Cú pháp:

```java
PaymentGateway gateway = provider.getIfAvailable();
if (gateway != null) {
    gateway.pay();
}
```

`getIfAvailable()` trả `null` khi **không có bean đáp ứng điều kiện**. Khác với `getObject()`, tình huống thiếu bean được xem là một kết quả hợp lệ. Tuy nhiên, nếu có hai gateway ngang nhau và không xác định được ứng viên thắng, lời gọi **vẫn ném `NoUniqueBeanDefinitionException`**.

Một tình huống thích hợp là dịch vụ gửi email tùy chọn:

```java
// Đoạn minh họa dùng NotificationSender do ứng dụng tự định nghĩa.
final class OrderService {
    private final ObjectProvider<NotificationSender> senders;

    OrderService(ObjectProvider<NotificationSender> senders) {
        this.senders = senders;
    }

    void completed(Order order) {
        senders.ifAvailable(sender -> sender.send(order));
    }
}
```

Đoạn này minh họa cách dùng, không phải file Java chạy độc lập: `NotificationSender` và `Order` là các kiểu nghiệp vụ chưa được định nghĩa. Ý nghĩa của `ifAvailable(Consumer<T>)` là chỉ gọi callback khi một bean có thể được phân giải. Nếu không có bean, không gọi callback. Nếu nhiều bean mơ hồ, vẫn có thể phát sinh `NoUniqueBeanDefinitionException`.

### Cung cấp giá trị mặc định bằng `Supplier`

`getIfAvailable(Supplier<T> defaultSupplier)` cho phép tạo object thay thế khi **không có bean**:

```java
PaymentGateway selected = provider.getIfAvailable(
        () -> new OfflinePaymentGateway()
);
```

Đây là đoạn minh họa với các kiểu ứng dụng; chỉ chạy sau khi định nghĩa `PaymentGateway` và `OfflinePaymentGateway`.

Điểm cần nhớ:

- Supplier thường **chỉ được gọi khi không có bean phù hợp**. Không nên thực hiện side effect trong supplier.
- Supplier **không được dùng để che lỗi ambiguity**. Hai gateway không primary vẫn khiến `getIfAvailable(supplier)` ném `NoUniqueBeanDefinitionException`.
- Object do supplier tự tạo bằng `new` **không mặc nhiên là bean do Spring quản lý**: không tự có dependency injection, lifecycle callbacks, proxy AOP hay destruction callback của container.
- Nếu fallback là đối tượng cần Spring quản lý, nên khai báo fallback như bean có điều kiện phù hợp, hoặc thiết kế cơ chế lựa chọn bean minh bạch.

Với chính sách “tính năng tùy chọn”, dùng `getIfAvailable()` hoặc `ifAvailable(...)` thường diễn đạt đúng hơn việc bắt exception từ `getObject()` rồi bỏ qua.

## 5. `getIfUnique()`: không có lựa chọn rõ ràng thì không lấy

Cú pháp:

```java
PaymentGateway gateway = provider.getIfUnique();
if (gateway == null) {
    // Không có ứng viên nào hoặc vẫn còn nhiều ứng viên ngang nhau.
}
```

Khác biệt quan trọng so với `getIfAvailable()` là **`getIfUnique()` trả `null` cả khi có nhiều bean nhưng không thể chọn một bean duy nhất**. Do đó nó thích hợp khi một hành vi chỉ nên bật nếu dependency có thể được suy ra an toàn từ cấu hình, và việc bỏ qua chức năng là chấp nhận được.

Hạn chế: giá trị `null` **không chỉ ra lý do**. Nếu nghiệp vụ cần báo lỗi thiếu cấu hình khác với cấu hình mơ hồ, `getIfUnique()` không đủ thông tin để phân biệt; nên chọn `getObject()`/`getIfAvailable()` và xử lý exception tương ứng.

Dạng có giá trị mặc định:

```java
PaymentGateway gateway = provider.getIfUnique(
        () -> new OfflinePaymentGateway()
);
```

`getIfUnique(Supplier<T>)` dùng fallback **khi không có ứng viên hoặc khi không thể xác định ứng viên thắng**. Đây chính là khác biệt với `getIfAvailable(Supplier<T>)`.

Ngoài ra, `ifUnique(Consumer<T>)` chạy callback nếu chọn được một bean duy nhất, còn khi không có bean hoặc nhiều bean mơ hồ thì không gọi callback. Các lỗi thực sự khi khởi tạo bean vẫn có thể được ném ra.

### Khi nào là “im lặng sai”?

Đừng dùng `getIfUnique()` cho quy trình bắt buộc cần đúng một gateway đã cấu hình: nếu hệ thống vô tình có hai gateway ngang nhau, `null` có thể làm bạn tưởng rằng tính năng bị tắt hợp lệ. Với dependency bắt buộc, `getObject()` sẽ phát hiện lỗi cấu hình sớm hơn tại thời điểm gọi.

## 6. Ví dụ Java 17+ hoàn chỉnh: bốn tình huống bean resolution

Ví dụ sau tạo **bốn `ApplicationContext` tách biệt** để kiểm tra bảng hành vi: không có gateway, có một gateway, có hai gateway không primary, và có hai gateway với một primary. Mỗi context dùng `@Configuration` và `@Bean` tiêu chuẩn; không cần Spring Boot.

### 6.1. Cấu trúc

```text
object-provider-demo/
├── settings.gradle
├── build.gradle
└── src/
    └── main/
        └── java/
            └── ObjectProviderDemo.java
```

`settings.gradle`:

```groovy
rootProject.name = 'object-provider-demo'
```

`build.gradle`:

```groovy
plugins {
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-context:6.2.19'
}

application {
    mainClass = 'ObjectProviderDemo'
}
```

Sử dụng Spring Framework `6.2.19` (artifact có trên Maven Central) và JDK 17 trở lên. Gradle cần là phiên bản tương thích với JDK được sử dụng.

### 6.2. Mã nguồn `src/main/java/ObjectProviderDemo.java`

```java
import java.util.function.Supplier;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

public class ObjectProviderDemo {

    interface Gateway {
        String name();
    }

    record NamedGateway(String name) implements Gateway {
    }

    @Configuration(proxyBeanMethods = false)
    static class EmptyConfig {
        // Không có Gateway bean.
    }

    @Configuration(proxyBeanMethods = false)
    static class SingleConfig {
        @Bean
        Gateway card() {
            return new NamedGateway("card");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class AmbiguousConfig {
        @Bean
        Gateway card() {
            return new NamedGateway("card");
        }

        @Bean
        Gateway bank() {
            return new NamedGateway("bank");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class PrimaryConfig {
        @Bean
        @Primary
        Gateway card() {
            return new NamedGateway("card");
        }

        @Bean
        Gateway bank() {
            return new NamedGateway("bank");
        }
    }

    private static String describe(Supplier<Gateway> action) {
        try {
            Gateway gateway = action.get();
            return gateway == null ? "null" : gateway.name();
        } catch (NoUniqueBeanDefinitionException ex) {
            return "NoUniqueBeanDefinitionException";
        } catch (NoSuchBeanDefinitionException ex) {
            return "NoSuchBeanDefinitionException";
        }
    }

    private static void runCase(String label, Class<?> configuration) {
        try (var context =
                     new AnnotationConfigApplicationContext(configuration)) {
            ObjectProvider<Gateway> provider =
                    context.getBeanProvider(Gateway.class);

            System.out.println("== " + label + " ==");
            System.out.println("getObject: " +
                    describe(provider::getObject));
            System.out.println("getIfAvailable: " +
                    describe(provider::getIfAvailable));
            System.out.println("getIfUnique: " +
                    describe(provider::getIfUnique));
            System.out.println("available/default: " +
                    describe(() -> provider.getIfAvailable(
                            () -> new NamedGateway("fallback"))));
            System.out.println("unique/default: " +
                    describe(() -> provider.getIfUnique(
                            () -> new NamedGateway("fallback"))));
        }
    }

    public static void main(String[] args) {
        runCase("EMPTY", EmptyConfig.class);
        runCase("SINGLE", SingleConfig.class);
        runCase("AMBIGUOUS", AmbiguousConfig.class);
        runCase("PRIMARY", PrimaryConfig.class);
    }
}
```

### 6.3. Chạy chương trình

Trong thư mục dự án:

```bash
gradle run --quiet
```

Nếu sử dụng Gradle Wrapper thì dùng `./gradlew run --quiet` trên macOS/Linux hoặc `.\gradlew.bat run --quiet` trên Windows PowerShell.

Các dòng do `System.out.println` của chương trình tạo ra:

```text
== EMPTY ==
getObject: NoSuchBeanDefinitionException
getIfAvailable: null
getIfUnique: null
available/default: fallback
unique/default: fallback
== SINGLE ==
getObject: card
getIfAvailable: card
getIfUnique: card
available/default: card
unique/default: card
== AMBIGUOUS ==
getObject: NoUniqueBeanDefinitionException
getIfAvailable: NoUniqueBeanDefinitionException
getIfUnique: null
available/default: NoUniqueBeanDefinitionException
unique/default: fallback
== PRIMARY ==
getObject: card
getIfAvailable: card
getIfUnique: card
available/default: card
unique/default: card
```

### 6.4. Phân tích từng cấu hình

**EMPTY:** Không có bean kiểu `Gateway`. `getObject()` ném lỗi; hai phương thức `getIf...` không có supplier trả `null`. Cả hai overload với supplier trả object `NamedGateway("fallback")` do code tạo ra.

**SINGLE:** Chỉ một bean `card`; mọi phương thức đều trả bean đó, fallback supplier không cần thực thi.

**AMBIGUOUS:** `card` và `bank` đều cùng kiểu, không có `@Primary` hay dấu hiệu phân giải duy nhất. `getObject()` và cả hai biến thể `getIfAvailable` ném `NoUniqueBeanDefinitionException`. `getIfUnique()` trả `null`; biến thể có supplier trả `fallback`.

**PRIMARY:** Vẫn có hai bean nhưng `card` là primary duy nhất. Cả ba phương thức đều trả `card`, không có ambiguity. Điều này chứng minh `getIfUnique()` không đòi container chỉ có đúng một bean definition.

Các chuỗi lỗi xuất hiện ở đây do helper `describe` **bắt và in tên exception**. Nếu bỏ helper và gọi `getObject()` trực tiếp trong trường hợp EMPTY/AMBIGUOUS, chương trình sẽ phát sinh exception thay vì in tên exception. Ví dụ được thiết kế để kiểm thử mọi case trong cùng một lần chạy.

## 7. Cách tiêm `ObjectProvider` vào bean ứng dụng

### 7.1. Constructor injection

`ObjectProvider<T>` thường được truyền qua constructor, giống các dependency khác:

```java
// Mã minh họa sử dụng kiểu Gateway của ứng dụng.
final class CheckoutService {
    private final ObjectProvider<Gateway> gateways;

    CheckoutService(ObjectProvider<Gateway> gateways) {
        this.gateways = gateways;
    }

    String checkout() {
        Gateway selected = gateways.getObject();
        return "Using " + selected.name();
    }
}
```

Đây là đoạn mã **minh họa**, không tự compile nếu chưa khai báo `Gateway` và đăng ký `CheckoutService` làm bean. Constructor chỉ giữ provider; bean mục tiêu được resolve khi gọi `checkout()`. Nếu nghiệp vụ luôn yêu cầu một `Gateway` và không có lý do resolve muộn, tiêm trực tiếp `Gateway` vẫn đơn giản hơn và kiểm tra cấu hình ngay khi khởi tạo service.

### 7.2. Phối hợp với `@Qualifier`

Khi có nhiều gateway, qualifier giúp thu hẹp tập ứng viên **trước khi** ba phương thức thực hiện phân giải:

```java
// Đoạn minh họa: khai báo ở bean.
@Bean
@Qualifier("external")
Gateway stripeGateway() {
    return new NamedGateway("stripe");
}

// Đoạn minh họa: tham số constructor một bean khác.
CheckoutService(
        @Qualifier("external") ObjectProvider<Gateway> externalGateway) {
    this.gateways = externalGateway;
}
```

Nếu chỉ có một gateway cùng kiểu mang qualifier `external`, `externalGateway.getObject()`, `getIfAvailable()` và `getIfUnique()` đều có thể chọn nó. Nếu có hai bean cùng qualifier và không có ứng viên thắng rõ ràng thì ambiguity vẫn xảy ra như trong bảng.

**Lưu ý:** Không dùng `ObjectProvider<Gateway>` rồi mong Spring tự chọn bean theo tên tham số trong mọi trường hợp. Khi vai trò nghiệp vụ rõ ràng, sử dụng `@Qualifier` hoặc tên bean/metadata có chủ đích.

### 7.3. Lấy provider từ `ApplicationContext`/`BeanFactory`

Ví dụ ở mục 6 gọi:

```java
ObjectProvider<Gateway> provider =
        context.getBeanProvider(Gateway.class);
```

Cách này hữu ích trong code điều phối container, tích hợp framework hoặc ở một điểm đã có `ApplicationContext`. Trong business service, constructor injection của `ObjectProvider<T>` thường diễn đạt dependency rõ hơn và giúp unit test dễ dàng hơn.

Với generic phức tạp (ví dụ `Repository<Customer>`), cách tìm bằng `getBeanProvider(Class<?>)` chỉ biểu diễn raw class. Có thể dùng biến thể nhận `ResolvableType` hoặc `ParameterizedTypeReference` khi cần giữ thông tin generic. Đừng cố truyền `List<Gateway>` vào `getBeanProvider(...)` và cho rằng Spring sẽ tự tổng hợp mọi Gateway; muốn duyệt nhiều bean, lấy provider theo kiểu **phần tử** rồi dùng `stream()`/`orderedStream()`.

## 8. Provider giải quyết lúc dùng: lazy, scope và cache

Một đặc tính quan trọng là **thời điểm resolve**. Tiêm `ObjectProvider<Gateway>` chưa bắt caller chọn ngay một `Gateway` ở constructor. Tuy nhiên, điều này **không tự bảo đảm mọi singleton Gateway được trì hoãn khởi tạo**.

Với Spring container mặc định, các **singleton không đánh dấu lazy** có thể được tạo sẵn khi context `refresh()`. Để trì hoãn việc tạo target bean đến lúc thật sự cần, cần kết hợp cơ chế thích hợp như `@Lazy` trên bean definition:

```java
@Bean
@Lazy
Gateway expensiveGateway() {
    return new ExpensiveGateway();
}
```

Đây là đoạn minh họa, cần định nghĩa `ExpensiveGateway` và import `@Lazy` trong dự án thực tế. Khi một code khác trực tiếp cần bean đó, Spring vẫn có thể khởi tạo nó; lazy là thuộc tính vòng đời và nhu cầu sử dụng thực tế, không phải cam kết bean không bao giờ được tạo.

### Mỗi lần gọi có tạo object mới không?

**Không nhất thiết.** Provider phân giải bean qua Spring container; **scope** mới quyết định có dùng lại instance hay tạo instance mới:

| Scope của target bean | Hai lần gọi `provider.getObject()` |
| --- | --- |
| `singleton` (mặc định) | Thông thường cùng một instance Spring quản lý |
| `prototype` | Thông thường tạo instance mới cho mỗi lần resolve |
| Request/session scope | Tùy scope hiện đang hoạt động và proxy được cấu hình |

Nếu một application phải tạo mới một đối tượng xử lý theo từng request, hãy dùng scope phù hợp hoặc factory có chủ đích. **`ObjectProvider` không có nghĩa là prototype**.

### Provider có “nhìn thấy” thay đổi bean registry không?

Provider thu được từ `BeanFactory` hoạt động trên state của factory. Về nguyên tắc, lời gọi tiếp theo có thể resolve theo trạng thái hiện tại của container, kể cả các thay đổi đăng ký bean được hỗ trợ. Tuy nhiên, application không nên coi việc thêm/xóa bean động sau khi context vận hành như một cơ chế tùy ý an toàn cho production: nhiều cấu trúc bean lifecycle/extension point giả định container đã được cấu hình ổn định. Sử dụng provider trước tiên cho optionality, lazy lookup và chọn bean theo tiêu chí.

## 9. Chọn API nào theo chính sách nghiệp vụ?

| Nhu cầu | Gợi ý | Lý do |
| --- | --- | --- |
| Tại lúc sử dụng bắt buộc có một bean hợp lệ | `getObject()` | Thiếu và ambiguity đều được phát hiện như lỗi cấu hình |
| Tính năng tùy chọn, thiếu bean là bình thường, nhiều bean là lỗi | `getIfAvailable()` | Trả `null` nếu thiếu, vẫn báo ambiguity |
| Cần object thay thế khi không đăng ký bean | `getIfAvailable(supplier)` | Fallback chỉ cho trường hợp không tìm thấy bean |
| Chỉ sử dụng bean khi chọn được một ứng viên thắng; còn lại bỏ qua | `getIfUnique()` | Trả `null` nếu thiếu **hoặc** ambiguity |
| Bỏ qua bean khi thiếu/mơ hồ, thay bằng object khác | `getIfUnique(supplier)` | Fallback được dùng cho cả hai trường hợp |
| Thực hiện side effect khi có ứng viên hợp lệ | `ifAvailable(consumer)` / `ifUnique(consumer)` | Không phải tự kiểm tra `null` |
| Muốn duyệt tất cả bean cùng kiểu | `stream()` / `orderedStream()` | Tránh buộc một bean thắng trong selection đơn |
| Dependency bắt buộc ngay từ lúc tạo service | Tiêm trực tiếp `T` | Ngắn gọn và phát hiện sai cấu hình khi tạo bean |
| Dependency tùy chọn được resolve một lần ở constructor | `Optional<T>` có thể phù hợp | Nhưng không có chính sách linh hoạt khi nhiều ứng viên mơ hồ |

Cần phân biệt các trường hợp “không có”, “không duy nhất” và “tạo bean thất bại”. Không đưa cùng một fallback cho tất cả nếu chúng mang ý nghĩa nghiệp vụ khác nhau.

### `Optional<T>` khác `ObjectProvider<T>` ở đâu?

Constructor injection với `Optional<Gateway>` biểu diễn “Gateway có thể không tồn tại” tại thời điểm tạo bean. Khi có nhiều Gateway mơ hồ, đó vẫn là vấn đề phân giải bean. `ObjectProvider<Gateway>` cung cấp handle để **truy vấn về sau**, có thể gọi nhiều lần và chọn chính sách `getObject`, `getIfAvailable` hoặc `getIfUnique` tùy tình huống.

Nếu một đối tượng chỉ cần đọc một dependency tùy chọn đúng một lần, `Optional<T>` có thể làm ý định rõ hơn. Nếu cần lazily resolve hoặc đếm/duyệt tập bean, `ObjectProvider<T>` thích hợp hơn.

## 10. Lỗi thường gặp và hướng sửa

| Triệu chứng | Nguyên nhân | Cách xử lý |
| --- | --- | --- |
| `getObject()` ném `NoSuchBeanDefinitionException` | Không có bean phù hợp kiểu/qualifier | Kiểm tra `@Component`/`@Bean`, component scan, profile, conditional configuration |
| `getIfAvailable()` vẫn ném exception dù tên chứa “Available” | Có nhiều bean và không có ứng viên thắng | Thêm `@Qualifier`/`@Primary` theo thiết kế, hoặc dùng `getIfUnique()` nếu bỏ qua ambiguity là đúng |
| `getIfUnique()` trả `null` dù có nhiều bean | Không chọn được ứng viên thắng duy nhất | Đây là hành vi thiết kế; quyết định rõ muốn fallback hay báo lỗi |
| `getIfUnique()` trả bean mặc dù có hai bean cùng kiểu | Có một bean được Spring ưu tiên, ví dụ `@Primary` | Xem selection policy, không đếm bean thô để kết luận |
| `getIfAvailable(supplier)` không chạy supplier khi hai bean mơ hồ | Supplier chỉ dùng khi thiếu bean | Dùng `getIfUnique(supplier)` nếu chính sách cho phép fallback lúc không duy nhất |
| Ném `NullPointerException` sau `getIfAvailable()` | Không kiểm tra kết quả `null` | Dùng `if (bean != null)`, `ifAvailable(...)` hoặc overload supplier |
| Object fallback không được AOP/inject | Tạo trực tiếp bằng `new` trong supplier | Đăng ký fallback với container hoặc tự cung cấp đầy đủ dependency |
| Provider không làm giảm thời gian startup | Target singleton vẫn eager | Kiểm tra `@Lazy` và dependency graph; chỉ tiêm provider không bảo đảm lazy-init |
| Hai lần `getObject()` trả cùng instance dù mong đối tượng mới | Target bean scope singleton | Chuyển sang prototype khi thật sự phù hợp hoặc dùng factory rõ ràng |
| `NoUniqueBeanDefinitionException` bị bắt như lỗi “không có bean” | Exception này là subtype của `NoSuchBeanDefinitionException` | Catch subtype trước, sau đó catch superclass |
| Cache nhầm `getIfUnique() == null` thành “không cấu hình” | `null` có thể nghĩa thiếu **hoặc** mơ hồ | Chỉ cache kết luận nếu ứng dụng thật sự không cần phân biệt hai tình huống |
| `getBeanProvider(List.class)` không tìm được tập Gateway | Tìm bean có kiểu List, không tổng hợp mọi Gateway | Dùng `getBeanProvider(Gateway.class).stream()`/`orderedStream()` |

## 11. Bài tập tự kiểm tra

**Bài 1 — Giá trị mặc định.** Trong `AmbiguousConfig` ở mục 6, dự đoán kết quả của hai lời gọi:

```java
provider.getIfAvailable(() -> new NamedGateway("fallback"));
provider.getIfUnique(() -> new NamedGateway("fallback"));
```

Đáp án: lời gọi thứ nhất ném `NoUniqueBeanDefinitionException`; lời gọi thứ hai trả `fallback`. “Có bean” không đồng nghĩa “có một bean có thể chọn”.

**Bài 2 — Chọn ứng viên primary.** Gắn `@Primary` cho bean `bank()` của `AmbiguousConfig`. Khi đó ba lời gọi `getObject()`, `getIfAvailable()` và `getIfUnique()` đều trả bean nào? Đáp án: `bank`, giả sử không có điều kiện khác làm bean này không hợp lệ.

**Bài 3 — Hai primary.** Nếu cả `card()` và `bank()` đều được gắn `@Primary` thì `getObject()` và `getIfAvailable()` có thể phân giải bằng primary không? Không: primary không còn duy nhất, có thể phát sinh `NoUniqueBeanDefinitionException`. `getIfUnique()` thường trả `null` khi không thể phân giải một ứng viên thắng (các lỗi tạo bean độc lập vẫn có thể được ném).

**Bài 4 — Optional feature.** Module hiển thị hóa đơn chỉ gửi email khi có bean `InvoiceMailer`; thiếu mailer là bình thường, nhưng hai mailer cùng kiểu là lỗi triển khai. Nên chọn gì? `getIfAvailable()` hoặc `ifAvailable(...)`: cho phép thiếu bean, vẫn phát hiện ambiguity.

**Bài 5 — Cấu hình nhập nhằng.** Một plugin chỉ chạy nếu hệ thống có lựa chọn `Plugin` được xác định rõ; nếu không có hoặc không chọn được, hãy bỏ qua an toàn. Nên chọn gì? `getIfUnique()`/`ifUnique(...)`, nếu thực sự chấp nhận bỏ qua tình huống ambiguity.

**Bài 6 — Vòng đời.** Hai lần `provider.getObject()` có luôn tạo hai instance mới không? Không; điều đó phụ thuộc scope của bean. Với singleton thông thường, hai lần trả cùng instance.

**Bài 7 — Điều gì bị che giấu?** Một developer đổi tất cả lời gọi `getObject()` thành `getIfUnique()` rồi kiểm tra `null`. Rủi ro là gì? Cấu hình hai bean không phân giải được có thể bị xem nhầm như “tính năng tùy chọn chưa cấu hình”, khiến lỗi thiết lập không được báo cáo.

## 12. Kết luận và nguồn tham khảo

- `getObject()`: **phải có một bean thắng**, nếu không báo lỗi.
- `getIfAvailable()`: **thiếu bean trả null**, nhưng **nhiều bean mơ hồ vẫn báo lỗi**.
- `getIfUnique()`: **thiếu hoặc mơ hồ trả null**, chỉ trả bean khi Spring phân giải được một lựa chọn duy nhất.
- Hai overload nhận `Supplier<T>` có chính sách fallback **khác nhau**, không được hoán đổi tùy ý.
- `@Primary`/`@Qualifier` và các quy tắc candidate resolution ảnh hưởng kết quả; tính unique là **unique sau phân giải**, không phải đơn giản đếm bean.
- `ObjectProvider` giải quyết *thời điểm và chính sách truy xuất*; scope và lazy initialization quyết định vòng đời instance.

Tài liệu chính thức:

- [Spring Framework 6.2 — `ObjectProvider<T>` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/beans/factory/ObjectProvider.html).
- [Spring Reference — Annotation-based container configuration và autowiring](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired.html).
- [Spring Reference — Primary và Fallback](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired-primary.html).
- [Spring Reference — Fine-tuning autowiring with qualifiers](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired-qualifiers.html).
- [Spring Framework 6.2 — `BeanFactory` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/beans/factory/BeanFactory.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="flexible-dependencies-007">Duyệt nhiều ứng viên bằng stream và orderedStream</a>

<details>
<summary>Xem chi tiết</summary>

# Duyệt nhiều ứng viên bằng stream và orderedStream trong Spring Core Container

## Mục tiêu

Khi ứng dụng có nhiều bean triển khai cùng một interface — ví dụ nhiều bước kiểm tra, bộ chuyển đổi, handler, plugin hoặc chiến lược nghiệp vụ — đôi khi ta muốn **lấy tất cả các bean tương thích rồi duyệt qua chúng**. Spring Core Container hỗ trợ cách làm này thông qua `org.springframework.beans.factory.ObjectProvider<T>`:

- `provider.stream()` cung cấp một Java `Stream<T>` tuần tự gồm các bean phù hợp, **không có cam kết thứ tự**.
- `provider.orderedStream()` cung cấp một Java `Stream<T>` tuần tự gồm các bean phù hợp đã được **Spring sắp xếp theo bộ so sánh thứ tự bean**.

Hai phương thức trên thuộc cơ chế IoC/Dependency Injection của Spring, **không phải reactive stream** như Reactor `Flux`, stream dữ liệu qua HTTP hay Java virtual thread. Kết quả cuối cùng là một `java.util.stream.Stream` thông thường, dùng các thao tác Java như `map`, `filter`, `forEach`, `findFirst` và `toList`.

Bài này hướng dẫn cách tiêm provider, kiểm soát thứ tự bằng `@Order`/`Ordered`, kết hợp `@Qualifier` và generic, phân biệt trường hợp 0–n bean, hiểu lazy/scope, tránh lỗi nghiệp vụ và thực hành bằng chương trình Java 17 chạy được.

## 1. Từ một dependency đến nhiều ứng viên

Giả sử ứng dụng có ba bean triển khai interface sau:

```java
interface Rule {
    String name();
}
```

Ba bean có thể thực hiện kiểm tra đầu vào, bổ sung thông tin, hoặc ghi dữ liệu phân tích. Nếu consumer khai báo duy nhất `Rule rule`, Spring phải tìm **một** ứng viên; khi có nhiều bean mà không chọn được một cách rõ ràng, ứng dụng có thể lỗi `NoUniqueBeanDefinitionException`.

Nếu muốn xử lý **tất cả** các ứng viên, có các lựa chọn:

| Cách nhận dependency | Ý nghĩa | Khi nên dùng |
| --- | --- | --- |
| `Rule rule` | Chọn một bean `Rule` | Phải có đúng một strategy hoặc chiến lược đã được chọn rõ |
| `List<Rule> rules` | Collection các bean phù hợp được tiêm khi tạo consumer | Pipeline cố định, tập thành phần được xác định tại thời điểm khởi tạo |
| `ObjectProvider<Rule> provider` | Giữ khả năng tra cứu bean khi cần | Lookup linh hoạt, truy vấn tại thời điểm xử lý |
| `provider.stream()` | Lấy nhiều bean phù hợp, không cam kết thứ tự | Kiểm tra membership, thống kê, tác vụ độc lập thứ tự |
| `provider.orderedStream()` | Lấy nhiều bean phù hợp theo thứ tự Spring | Pipeline, chuỗi kiểm tra hoặc callback có mức ưu tiên |

Ví dụ ý tưởng với provider:

```java
// Đoạn minh họa trong một bean do Spring quản lý.
private final ObjectProvider<Rule> rules;

Pipeline(ObjectProvider<Rule> rules) {
    this.rules = rules;
}

void execute() {
    rules.orderedStream().forEach(rule -> runRule(rule));
}
```

Trong đoạn minh họa, `runRule` là method do ứng dụng tự xây; import provider bằng:

```java
import org.springframework.beans.factory.ObjectProvider;
```

Một `ObjectProvider<T>` có thể được Spring tiêm **ngay cả khi không tồn tại bean `T` nào**. Sự vắng mặt của ứng viên lúc duyệt sẽ tạo **stream rỗng**, thay vì một collection `null`.

## 2. Khác biệt chính xác giữa `stream()` và `orderedStream()`

### `stream()` không cam kết thứ tự

```java
provider.stream()
        .map(Rule::name)
        .forEach(System.out::println);
```

Provider trả một stream tuần tự gồm các instance bean phù hợp. Theo hợp đồng Spring, thứ tự của `stream()` **không được bảo đảm**; trong một số ứng dụng bạn có thể thấy thứ tự tương tự thứ tự đăng ký bean, nhưng đó không phải căn cứ để thiết kế nghiệp vụ.

Nếu chỉ muốn kiểm kê bean theo tên, có thể tự sắp xếp kết quả:

```java
List<String> names = provider.stream()
        .map(Rule::name)
        .sorted()
        .toList();
```

Ở đây `sorted()` là phép sắp xếp **theo tên do ứng dụng lựa chọn**, không phải sắp xếp theo `@Order` của Spring. Method `Stream.toList()` có từ Java 16, nên chạy được trên Java 17.

### `orderedStream()` tuân theo thứ tự Spring

```java
provider.orderedStream()
        .forEach(rule -> runRule(rule));
```

Spring dùng *common order comparator* của bean factory để sắp xếp các bean trong stream. Với cấu hình annotation thông thường, các chỉ dẫn quan trọng là:

- `org.springframework.core.Ordered` — đối tượng có thể cung cấp số thứ tự qua `getOrder()`.
- `org.springframework.core.annotation.Order` — khai báo mức ưu tiên trên class bean hoặc phương thức `@Bean`.
- `jakarta.annotation.Priority` — annotation chuẩn trên class được Spring nhận diện trong cơ chế sắp thứ tự.

**Số order nhỏ hơn được xếp trước.** Ví dụ `@Order(10)` được ưu tiên trước `@Order(30)`. Hai bean cùng số order hoặc không khai báo order không tạo một hợp đồng nghiệp vụ chắc chắn về thứ tự tương đối; nếu hai bước phải chạy trước–sau, hãy gán ưu tiên rõ ràng hoặc tự xây comparator có khóa phụ.

`@Order` là thứ tự **khi lấy nhiều bean theo cơ chế sắp xếp**, không phải lệnh buộc Spring khởi tạo bean có số nhỏ trước bean có số lớn. Thời điểm khởi tạo phụ thuộc quan hệ dependency và vòng đời Spring.

### So sánh trong một ví dụ

Giả sử:

| Bean | Giá trị `Rule.name()` | Order |
| --- | --- | --- |
| `validationRule` | `validate` | 30 |
| `enrichmentRule` | `enrich` | 10 |
| `analyticsRule` | `analytics` | 20 |

Khi dùng `stream().map(Rule::name).sorted().toList()`, kết quả là `[analytics, enrich, validate]` vì được sắp theo **tên**.

Khi dùng `orderedStream().map(Rule::name).toList()`, kết quả là `[enrich, analytics, validate]` vì được sắp theo **10, 20, 30**.

Không nên dùng kết quả thứ nhất làm bằng chứng về thứ tự gốc của `stream()`; chính phép `sorted()` đã tạo ra thứ tự có thể dự đoán.

## 3. Ví dụ đầy đủ có thể biên dịch và chạy

Chương trình sau dùng Spring Framework thuần, Java 17 và Maven. Nó minh họa cả hai API, `@Qualifier` lọc nhiều bean, `@Primary` chọn một bean và provider không có ứng viên.

### 3.1. Cấu trúc dự án

```text
provider-stream-demo/
├── pom.xml
└── src/main/java/demo/ProviderStreamDemo.java
```

Nội dung `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>demo</groupId>
    <artifactId>provider-stream-demo</artifactId>
    <version>1.0-SNAPSHOT</version>

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
```

Nội dung `src/main/java/demo/ProviderStreamDemo.java`:

```java
package demo;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;

public class ProviderStreamDemo {

    interface Rule {
        String name();
    }

    record NamedRule(String name) implements Rule {
    }

    static final class ReportService {
        private final ObjectProvider<Rule> all;
        private final ObjectProvider<Rule> mandatory;
        private final ObjectProvider<Rule> missing;
        private final Rule preferred;

        ReportService(ObjectProvider<Rule> all,
                      ObjectProvider<Rule> mandatory,
                      ObjectProvider<Rule> missing,
                      Rule preferred) {
            this.all = all;
            this.mandatory = mandatory;
            this.missing = missing;
            this.preferred = preferred;
        }

        void printReport() {
            // Sắp THEO TÊN để output ổn định, không dựa vào
            // thứ tự mặc định của ObjectProvider.stream().
            System.out.println("all.byName=" +
                    all.stream().map(Rule::name).sorted().toList());

            // Thứ tự do Spring quyết định theo @Order.
            System.out.println("all.bySpringOrder=" +
                    all.orderedStream().map(Rule::name).toList());

            // Chỉ lấy những Rule có qualifier "mandatory".
            System.out.println("mandatory.bySpringOrder=" +
                    mandatory.orderedStream().map(Rule::name).toList());

            // Không có ứng viên: stream rỗng.
            System.out.println("missing.size=" +
                    missing.stream().toList().size());

            // Chọn một bean dùng @Primary.
            System.out.println("single.preferred=" + preferred.name());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class AppConfig {

        @Bean("validationRule")
        @Order(30)
        @Qualifier("mandatory")
        Rule validationRule() {
            return new NamedRule("validate");
        }

        @Bean("enrichmentRule")
        @Order(10)
        @Qualifier("mandatory")
        Rule enrichmentRule() {
            return new NamedRule("enrich");
        }

        @Bean("analyticsRule")
        @Order(20)
        @Primary
        @Qualifier("metrics")
        Rule analyticsRule() {
            return new NamedRule("analytics");
        }

        @Bean
        ReportService reportService(
                ObjectProvider<Rule> all,
                @Qualifier("mandatory") ObjectProvider<Rule> mandatory,
                @Qualifier("notRegistered") ObjectProvider<Rule> missing,
                Rule preferred) {
            return new ReportService(all, mandatory, missing, preferred);
        }
    }

    public static void main(String[] args) {
        try (var context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {
            context.getBean(ReportService.class).printReport();
        }
    }
}
```

Mở terminal tại thư mục `provider-stream-demo` và chạy (yêu cầu JDK 17+ và Maven):

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.ProviderStreamDemo
```

Các dòng **chương trình tự in ra** (không tính thông điệp logging do môi trường Maven hoặc Spring thêm vào):

```text
all.byName=[analytics, enrich, validate]
all.bySpringOrder=[enrich, analytics, validate]
mandatory.bySpringOrder=[enrich, validate]
missing.size=0
single.preferred=analytics
```

### 3.2. Giải thích từng kết quả

**`all.byName`:** `ObjectProvider<Rule> all` tìm cả ba bean `Rule`. Sau khi gọi `map(Rule::name).sorted()`, chúng được sắp theo thứ tự từ điển thành `analytics, enrich, validate`. Lời gọi `sorted()` là điều kiện cần để output này xác định.

**`all.bySpringOrder`:** `orderedStream()` dùng thứ tự đã khai báo. `enrichmentRule` có `@Order(10)`, `analyticsRule` có `@Order(20)`, và `validationRule` có `@Order(30)`. Vì vậy danh sách là `enrich, analytics, validate`.

**`mandatory.bySpringOrder`:** tham số `ObjectProvider<Rule> mandatory` mang `@Qualifier("mandatory")`. Spring chỉ chọn `enrichmentRule` và `validationRule`, sau đó sắp theo `10` rồi `30`. Một qualifier có thể được gắn cho **nhiều bean** cùng lúc.

**`missing.size`:** không có bean `Rule` với qualifier `notRegistered`. Spring vẫn inject được `ObjectProvider<Rule>`, và `stream()` cho một stream rỗng, nên kích thước bằng 0.

**`single.preferred`:** Spring cần chọn **một** `Rule` cho tham số `Rule preferred`, vì vậy `@Primary` của `analyticsRule` được áp dụng. Nhưng `@Primary` không loại các bean khác khỏi `all.stream()` hoặc `all.orderedStream()`.

### 3.3. Kiểm tra khi thay order

Hãy đổi `@Order(30)` trên `validationRule` thành `@Order(5)` rồi chạy lại:

- `all.byName` vẫn là `[analytics, enrich, validate]` vì vẫn sắp theo tên.
- `all.bySpringOrder` thành `[validate, enrich, analytics]` vì `5` đứng trước `10` và `20`.
- `mandatory.bySpringOrder` thành `[validate, enrich]`.
- `missing.size` và `single.preferred` không đổi.

Thử nghiệm này tách rõ **thứ tự trong Java Stream tự tạo** với **thứ tự BeanFactory của Spring**.

## 4. Thứ tự và lựa chọn ứng viên khi duyệt stream

`orderedStream()` dùng quy tắc order của Spring: giá trị nhỏ hơn được xếp trước. `Ordered#getOrder()` lấy giá trị từ instance, `@Order` khai báo trên class hoặc phương thức `@Bean`, còn `jakarta.annotation.Priority` áp dụng ở class. Khi hai bean cùng order, ứng dụng không nên giả định thứ tự tương đối luôn ổn định; pipeline bắt buộc trước–sau cần order riêng hoặc comparator rõ ràng.

Điểm cần kiểm tra khi dùng **provider stream** là metadata đi theo **bean definition**. Chẳng hạn `@Order(5)` trên phương thức `@Bean` có thể được Spring dùng khi `orderedStream()` sắp xếp, dù class của object được trả về không có annotation. Nếu lấy những object đó rồi tự gọi một comparator bên ngoài BeanFactory, metadata của factory method có thể không còn sẵn. Vì vậy hãy kiểm thử thứ tự tại chính điểm sử dụng provider.

| Cơ chế | Ảnh hưởng khi lấy stream bean |
| --- | --- |
| `@Qualifier` và kiểu generic ở injection point | Thu hẹp **ứng viên** trước khi duyệt |
| `@Order`, `Ordered`, `@Priority` | Sắp thứ tự **trong `orderedStream()`** |
| `@Primary` | Ưu tiên khi chọn **một bean**; không tự xóa phần tử khỏi stream |
| `@DependsOn` | Điều khiển quan hệ khởi tạo, không phải thứ tự duyệt |

`@Order` trên `@Configuration` không tự truyền xuống mọi `@Bean` bên trong. Bài về thứ tự collection đã giải thích chi tiết `PriorityOrdered`, thứ tự tie và factory method; tại đây chỉ cần biết các quy tắc ấy quyết định kết quả **`orderedStream()`**, còn `stream()` không cam kết thứ tự.

## 5. Lọc nhiều bean theo `@Qualifier` và generic

### 5.1. `@Qualifier` trên injection point của provider

Spring có thể xét qualifier khi tạo provider tại injection point:

```java
// Đoạn minh họa trong class Spring-managed:
private final ObjectProvider<Rule> mandatoryRules;

MyService(@Qualifier("mandatory") ObjectProvider<Rule> mandatoryRules) {
    this.mandatoryRules = mandatoryRules;
}
```

Khi sau đó gọi `mandatoryRules.stream()`/`orderedStream()`, tập ứng viên đã chịu điều kiện lọc tương ứng. Thực tế một qualifier như `mandatory` có thể gắn lên **hai hoặc nhiều bean**. Không nên dùng qualifier như một "lệnh chọn duy nhất" nếu ứng dụng cần một nhóm.

Có thể đặt qualifier trên bean đăng ký bằng `@Bean` hoặc `@Component`. Bean name cũng được dùng trong một số điều kiện chọn ứng viên, nhưng **tên bean** và **qualifier nghiệp vụ** là hai khái niệm nên thể hiện rõ trong thiết kế.

### 5.2. Generic type giúp thu hẹp ứng viên

Spring có thể dùng generic type của bean và injection point như một tiêu chí tìm ứng viên. Ví dụ:

```java
// Đoạn minh họa, cần đặt trong cấu hình/bean đầy đủ.
interface Decoder<T> {
    T decode(byte[] input);
}

@Bean
Decoder<String> stringDecoder() {
    return input -> new String(input,
            java.nio.charset.StandardCharsets.UTF_8);
}

@Bean
Decoder<byte[]> bytesDecoder() {
    return input -> input.clone();
}

// Trong constructor một bean khác:
DecoderRegistry(ObjectProvider<Decoder<String>> stringDecoders) {
    this.stringDecoders = stringDecoders;
}
```

Đây là minh họa để đưa vào cấu hình phù hợp, không phải chương trình độc lập. `ObjectProvider<Decoder<String>>` có thể chọn các bean với metadata generic phù hợp kiểu `Decoder<String>`. Kết quả phụ thuộc Spring có đủ thông tin generic ở bean definition; khai báo `@Bean` trả raw `Decoder` làm mất thông tin quan trọng.

Nếu mã dùng `ObjectProvider<Object>`, tức là nó tìm một tập bean rất rộng và khó kiểm soát. Nên dùng interface hẹp, generic rõ ràng và qualifier có nghĩa nghiệp vụ.

### 5.3. Không tự nhầm lọc kiểu với lọc instance

`@Qualifier` và generic có thể ảnh hưởng **tập bean candidate** trước khi lấy stream. Trong khi đó, `provider.stream().filter(rule -> ...)` là phép Java Stream xử lý **đối tượng bean đã được lấy**. Đối với bean có khởi tạo tốn kém, sự khác nhau này có ý nghĩa: filter sau khi lấy bean không có tác dụng ngăn Spring tạo bean đó khi cần.

## 6. Không có bean, có một bean và có nhiều bean

`ObjectProvider<T>` phục vụ cả lookup đơn lẻ lẫn duyệt nhiều bean. Hãy phân biệt kết quả của từng API:

| Tập ứng viên phù hợp | `stream()` | `orderedStream()` | `getObject()` / `getIfAvailable()` |
| --- | --- | --- | --- |
| Không có bean nào | Stream rỗng | Stream rỗng | `getObject()` thường lỗi thiếu bean; `getIfAvailable()` trả `null` |
| Có một bean | Một phần tử | Một phần tử | Trả bean đó |
| Có nhiều bean | Nhiều phần tử | Nhiều phần tử theo Spring order | Phải phân giải ứng viên đơn lẻ; có thể lỗi nhập nhằng |

Khi có nhiều ứng viên, `stream()` **không mặc định chọn một trong số chúng**. Đó là lý do truy vấn stream thường đúng hơn gọi `getObject()` trong hệ thống plugin nhiều implementation.

Ví dụ kiểm tra không có ứng viên:

```java
// Minh họa trong class đã có ObjectProvider<Rule> rules.
if (rules.stream().findAny().isEmpty()) {
    System.out.println("No matching Rule beans");
}
```

Nếu có bean phù hợp nhưng nó ném exception trong constructor hoặc `@Bean` factory method, thao tác tiêu thụ stream **vẫn có thể ném lỗi tạo bean**. Đừng bắt mọi exception rồi coi như "không có rule": tập ứng viên rỗng là một trạng thái hợp lệ, còn bean tạo lỗi là sự cố cấu hình/chạy chương trình.

Lưu ý `getIfAvailable()` xử lý thiếu dependency, nhưng **không có nghĩa chắc chắn không gặp lỗi nhập nhằng** khi có nhiều ứng viên. Nếu chỉ muốn lấy một ứng viên trong tình huống duy nhất, xem hợp đồng của `getIfUnique()`/`ifUnique()`. Trong bài toán cần chạy 0..n rule, stream mới là API đúng ý định.

## 7. Java Stream: thao tác an toàn và thời điểm thực thi

Kết quả `ObjectProvider.stream()`/`orderedStream()` là Java `Stream<T>`, nên dùng được các operation quen thuộc:

```java
// Những đoạn minh họa dưới đây nằm trong class có provider:
// Lấy list ở thời điểm hiện tại:
List<Rule> current = provider.orderedStream().toList();

// Chỉ lấy tên, không phụ thuộc thứ tự gốc:
List<String> names = provider.stream()
        .map(Rule::name).sorted().toList();

// Lấy rule đầu tiên theo Spring order:
Optional<Rule> first = provider.orderedStream().findFirst();

// Thực hiện nghiệp vụ theo thứ tự Spring:
provider.orderedStream().forEach(rule -> apply(rule));
```

Các đoạn trên cần import `java.util.List` và `java.util.Optional`; method `apply` phải do ứng dụng định nghĩa.

### 7.1. Stream chỉ dùng một lượt

Không lưu trực tiếp một `Stream<Rule>` vào field rồi gọi nhiều terminal operations trên cùng đối tượng stream:

```java
// Mã minh họa lỗi:
Stream<Rule> once = provider.orderedStream();
long count = once.count();
List<Rule> again = once.toList(); // IllegalStateException hoặc lỗi reuse
```

Cách đúng là **gọi lại provider** để tạo stream mới hoặc thu thành `List<Rule>` nếu cần sử dụng nhiều lần trên một snapshot:

```java
List<Rule> snapshot = provider.orderedStream().toList();
long count = snapshot.size();
snapshot.forEach(rule -> apply(rule));
```

Với Java 17, `Stream.toList()` trả một list không cho phép sửa trực tiếp. Nếu muốn chỉnh collection, hãy tạo bản sao mutable có chủ đích.

### 7.2. Stream operations có thể dừng sớm

`findFirst()`, `anyMatch()` và `limit(n)` có thể chỉ đi qua một phần ứng viên. Dùng chúng cho truy vấn tìm kiếm, không dùng để bảo đảm mọi handler đã được gọi. Khi cần xử lý hết các rule, dùng `forEach` trên stream đã chọn. Đừng dùng `count()` rồi suy ra side effect trong các intermediate operations chắc chắn đã xảy ra: Java Stream có quyền tối ưu trong phạm vi hợp đồng API.

### 7.3. Không tự đổi sang xử lý song song

Hai phương thức Spring trả **sequential stream**. Gọi `parallel()` trên đó chuyển việc thực thi sang mô hình xử lý song song theo Java Stream; Spring order vẫn liên quan tới encounter order, nhưng side effect có thể chạy xen kẽ giữa các thread.

Một pipeline ghi database, gọi API hoặc phụ thuộc trạng thái của bước trước **không nên tự tiện thêm `parallel()`**. Muốn concurrency phải có thiết kế an toàn thread, giao dịch và ngữ nghĩa thứ tự riêng.

### 7.4. Muốn thứ tự khác Spring thì dùng comparator riêng

Ví dụ application muốn sắp theo `Rule.name()`:

```java
List<Rule> alphabetical = provider.stream()
        .sorted(java.util.Comparator.comparing(Rule::name))
        .toList();
```

Nếu hai rule trùng tên, comparator trả bằng nhau; khi cần thứ tự nghiệp vụ toàn phần, bổ sung định danh/khóa phụ rõ ràng. Đừng coi thứ tự bean registration là một tie-breaker ổn định.

## 8. Provider, lazy initialization và scope

`ObjectProvider<T>` được tiêm như một điểm truy cập vào BeanFactory. Khi consumer gọi provider, BeanFactory phân giải các ứng viên tại thời điểm đó.

Tuy nhiên có hai khía cạnh không nên nhầm:

**Thời điểm consumer gọi provider** khác **thời điểm singleton bean đã được tạo**. Spring mặc định có thể khởi tạo non-lazy singleton từ lúc context khởi động. Vì thế giữ `ObjectProvider<T>` trong field không đồng nghĩa các bean `T` đều chưa được tạo. `@Lazy` và quan hệ dependency quyết định việc trì hoãn khởi tạo.

**Tra cứu lại bean** khác **tạo instance mới**. Với singleton, hai lần gọi provider thường nhận lại cùng singleton. Với prototype, mỗi lần bean được phân giải có thể nhận instance mới theo cơ chế scope. Với scoped proxy, phải hiểu thêm cách proxy đại diện cho đối tượng hiện hành.

Khi pipeline cần một snapshot cố định:

```java
// Minh họa: phần constructor của class đã có provider.
this.rules = provider.orderedStream().toList();
```

Vì collection được xác định tại lúc constructor chạy, consumer có cách sử dụng giống danh sách cố định. Khi muốn lookup theo từng lượt:

```java
// Minh họa: phần method thực thi trong class đã có provider.
void process() {
    provider.orderedStream().forEach(this::processOne);
}
```

Đây là lựa chọn thiết kế có chủ đích, không phải một kiểu tự động thay đổi cấu hình Spring runtime. Phần lớn ứng dụng đăng ký bean trong giai đoạn khởi động; provider không bảo đảm mọi hệ thống có thể tùy ý hot-plug bean vào context đang chạy rồi tất cả thao tác đều nhận thấy thay đổi đó.

## 9. Các overload lọc kiểu trong Spring Framework 6.2

Ngoài `stream()`/`orderedStream()` không tham số, những bản Spring 6.2 đủ mới hỗ trợ bộ lọc **theo kiểu ứng viên**:

```java
// Chữ ký API minh họa, không phải class Java độc lập:
Stream<T> stream(Predicate<Class<?>> customFilter);
Stream<T> orderedStream(Predicate<Class<?>> customFilter);

Stream<T> stream(Predicate<Class<?>> customFilter,
                 boolean includeNonSingletons);
Stream<T> orderedStream(Predicate<Class<?>> customFilter,
                        boolean includeNonSingletons);
```

Hai overload nhận `Predicate<Class<?>>` xuất hiện từ Spring **6.2.3**; các overload nhận thêm `includeNonSingletons` xuất hiện từ **6.2.5**. Chúng có mặt ở Spring Framework 6.2.19 được dùng trong `pom.xml` của ví dụ.

- `customFilter` nhận **class kiểu ứng viên**, khác với `Stream.filter(Predicate<T>)` lọc theo instance đã được lấy.
- `includeNonSingletons` cho phép quyết định có tính tới prototype/scoped bean cùng với singleton trong overload tương ứng, thay vì chỉ singleton.
- Hằng `ObjectProvider.UNFILTERED` của API hỗ trợ việc bỏ lọc mặc định ở một số overload. Đây là khả năng nâng cao, có thể cho ra ứng viên khác với phạm vi autowire thường thấy. Đừng dùng tràn lan để vượt qua một qualifier đã được thiết kế có chủ đích.

Ví dụ cú pháp khi muốn lọc kiểu theo quy ước class name:

```java
// Đoạn minh họa trong class có ObjectProvider<Rule> provider:
List<Rule> selected = provider.orderedStream(
        type -> type.getSimpleName().endsWith("Rule"),
        true
).toList();
```

Ví dụ này cho thấy predicate nhận `Class<?>` chứ không nhận `Rule`. Nhưng tên class rất dễ đổi khi refactor/proxy/lambda; đối với quy tắc nghiệp vụ thực tế, qualifier hoặc interface chuyên biệt rõ ràng và ổn định hơn. Không cần những overload này cho hầu hết ứng dụng dùng `stream()`/`orderedStream()` bình thường.

## 10. Ứng dụng vào các tình huống điển hình

### 10.1. Pipeline có thứ tự nghiệp vụ

Ví dụ hệ thống xử lý đơn hàng có `ValidationStep`, `EnrichmentStep` và `PersistenceStep`. Nếu thứ tự bắt buộc là validate → enrich → persist, gán metadata order minh bạch và gọi `orderedStream()`. Trước khi triển khai, xác định bước nào được phép thất bại và khi nào dừng pipeline. `@Order` không tự định nghĩa rollback hay chính sách lỗi của từng bước.

### 10.2. Plugin độc lập, không quan tâm thứ tự

Một tác vụ thống kê số plugin, kiểm tra khả năng hỗ trợ hoặc xây tập tên có thể dùng `stream()`. Nếu in report ra cho người đọc, sắp xếp tên bằng Java Stream để output ổn định mà không ràng buộc business behavior vào bean registration.

### 10.3. Chọn handler đầu tiên phù hợp

```java
// Đoạn minh họa, interface Handler cần supports(Request) và handle(Request).
Optional<Handler> selected = handlers.orderedStream()
        .filter(handler -> handler.supports(request))
        .findFirst();

selected.ifPresent(handler -> handler.handle(request));
```

`findFirst()` lấy handler đầu tiên theo thứ tự đã định trong stream; ở đây `orderedStream()` có vai trò quyết định. Nếu nhiều handler cùng ưu tiên và cùng hỗ trợ request, cần một thứ tự phụ ổn định hoặc thay đổi thiết kế selection.

### 10.4. Đăng ký theo khóa nghiệp vụ

```java
// Minh họa trong class có ObjectProvider<Rule> provider:
Map<String, Rule> byBusinessName = provider.stream().collect(
        java.util.stream.Collectors.toMap(Rule::name, rule -> rule)
);
```

Khóa ở đây là kết quả `Rule.name()` do ứng dụng định nghĩa, không phải bean name của Spring. Nếu hai rule trả cùng tên, `Collectors.toMap` theo cấu hình này sẽ ném lỗi vì khóa trùng. Hãy định nghĩa chính sách trùng khóa rõ ràng thay vì tùy tiện cho phép ghi đè.

## 11. Lỗi thường gặp và hướng xử lý

| Hiện tượng | Nguyên nhân phổ biến | Cách sửa |
| --- | --- | --- |
| Rule chạy theo thứ tự ngoài ý muốn | Dựa vào thứ tự đăng ký khi gọi `stream()` | Dùng `orderedStream()` với metadata order đúng |
| `orderedStream()` không theo thứ tự dự kiến | `@Order` đặt sai cấp, bỏ sót bean, hai bean cùng order | Đặt order tại bean class/`@Bean`; dùng tie-breaker rõ |
| Bean mong đợi không xuất hiện | Chưa đăng ký, không nằm trong component scan, generic/qualifier không khớp hoặc không phải autowire candidate | Kiểm tra bean definition và injection point |
| Có nhiều bean nhưng `getObject()` lỗi | API đó phải trả một bean, trong khi ứng viên không xác định duy nhất | Dùng `stream()`/`orderedStream()` nếu thật sự cần tất cả |
| Chọn `@Primary` nhưng stream vẫn có nhiều bean | `@Primary` ưu tiên single-bean resolution, không lọc stream | Dùng qualifier/kiểu riêng nếu muốn một nhóm |
| Provider stream rỗng | Không có ứng viên hợp lệ tại injection point | Kiểm tra `T`, qualifier, generic và cấu hình đăng ký |
| Duyệt stream gặp lỗi tạo bean | Bean đích khởi tạo hoặc scope lỗi | Sửa exception gốc, không coi là tập rỗng |
| Dùng lại stream và bị lỗi | Stream đã qua terminal operation | Gọi provider để tạo stream mới hoặc lưu list snapshot |
| Chỉ chạy một handler dù muốn tất cả | Dùng `findFirst()`/`limit(1)` | Dùng `forEach` để xử lý hết |
| Handler chạy đồng thời ngoài dự kiến | Tự gọi `parallel()` và có side effect | Dùng sequential hoặc thiết kế concurrency có chủ đích |
| Kết quả khác giữa các môi trường | Dựa vào thứ tự không được bảo đảm hoặc thứ tự khi order trùng | Kiểm thử thứ tự bằng `@Order`/comparator và khóa phụ |

Khi debug, hãy kiểm tra theo trình tự: (1) bean đã đăng ký trong đúng context; (2) kiểu `T` và generic; (3) qualifier và autowire candidate; (4) đây là truy vấn nhiều hay một bean; (5) có metadata `@Order`/`Ordered` hay không; (6) bean có khởi tạo thất bại hoặc scope ngoài dự định hay không.

Cần nhớ thêm: `@Order` trên `@Configuration` không tự áp dụng lên các method `@Bean`, `@DependsOn` không phải thứ tự duyệt, và `ObjectProvider<T>` không phải cơ chế loại bỏ mọi lỗi bean creation.

## 12. Kiểm thử và bài tập

### Kiểm thử có ý nghĩa

Trong ví dụ `ProviderStreamDemo` ở mục 3, ta có thể đặt các assert sau vào method `printReport()` để kiểm tra đúng thỏa thuận giữa application và container:

```java
// Đoạn bổ sung dùng trong ReportService của chương trình mục 3.
if (!all.orderedStream().map(Rule::name).toList()
        .equals(java.util.List.of("enrich", "analytics", "validate"))) {
    throw new AssertionError("Wrong Spring order");
}

if (!mandatory.orderedStream().map(Rule::name).toList()
        .equals(java.util.List.of("enrich", "validate"))) {
    throw new AssertionError("Wrong mandatory selection or order");
}

if (!missing.stream().findAny().isEmpty()) {
    throw new AssertionError("Expected empty stream");
}
```

Không có dòng stdout mới khi các điều kiện đúng; khi sai, code ném `AssertionError` tương ứng. Các kiểm tra này mạnh hơn việc chụp thứ tự tình cờ từ `stream()` không sắp xếp.

### Bài tập 1: thay đổi `@Order`

Đổi `@Order(30)` của validation thành `@Order(5)`. Dự đoán hai dòng `all.bySpringOrder` và `mandatory.bySpringOrder`.

**Đáp án:** lần lượt là `[validate, enrich, analytics]` và `[validate, enrich]`. Dòng `all.byName` vẫn xếp theo tên, nên không đổi.

### Bài tập 2: mở rộng nhóm qualifier

Đổi qualifier trên `analyticsRule` từ `metrics` sang `mandatory`. Kết quả `mandatory.bySpringOrder` thế nào?

**Đáp án:** `[enrich, analytics, validate]` vì cả ba bean cùng đủ điều kiện và được sắp theo `10,20,30`.

### Bài tập 3: không còn bean `Rule`

Xóa các phương thức `@Bean` trả `Rule`. Đồng thời loại bỏ hoặc sửa tham số `Rule preferred` của `reportService` vì nó đang là dependency bắt buộc. `ObjectProvider<Rule>.orderedStream().toList()` cho kết quả gì?

**Đáp án:** một list rỗng; provider có thể được tiêm dù không có bean đích. Nếu vẫn giữ `Rule preferred` bắt buộc, context sẽ không dựng được consumer đó.

### Bài tập 4: dùng `List<T>` hay `ObjectProvider<T>`

Một consumer chỉ cần biết tất cả plugin **một lần tại thời điểm tạo bean**. Có cần dùng provider không?

**Đáp án:** không nhất thiết. `List<Plugin>` trực tiếp là thiết kế đơn giản và diễn đạt đúng yêu cầu. Provider thích hợp khi cần lookup theo từng lượt hoặc hành vi linh hoạt theo scope.

### Bài tập 5: hai bean cùng order

Nếu `validationRule` và `analyticsRule` cùng `@Order(20)` mà nghiệp vụ yêu cầu validation luôn trước analytics, có thể tin thứ tự xuất hiện tình cờ trong danh sách được không?

**Đáp án:** không nên. Hãy cho chúng order khác nhau hoặc định nghĩa comparator có khóa phụ ổn định và kiểm thử trường hợp trùng order.

## Kết luận kiến thức

`ObjectProvider<T>` cho phép một bean Spring truy vấn các ứng viên `T` khi cần. `stream()` phù hợp khi bạn muốn duyệt tất cả ứng viên mà không phụ thuộc thứ tự; `orderedStream()` phù hợp khi nghiệp vụ yêu cầu thứ tự do Spring xác lập bằng `Ordered`, `@Order` hoặc cơ chế annotation tương ứng.

Qualifier và generic quyết định **ứng viên nào tham gia**; order quyết định **trình tự duyệt**; `@Primary` thường giải quyết bài toán **chọn một bean**, không lọc cả stream. Khi không có ứng viên, stream rỗng là bình thường; khi bean được chọn khởi tạo thất bại, lỗi vẫn cần được xử lý. Java Stream vẫn có những đặc tính thông thường: tiêu thụ một lần, phép xử lý có thể dừng sớm, và concurrency phải được thiết kế riêng.

## Tài liệu chính thức

- Spring Framework — [`ObjectProvider<T>` Javadoc, 6.2.x](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/beans/factory/ObjectProvider.html): `stream()`, `orderedStream()`, bộ lọc kiểu và overload có tham số scope.
- Spring Framework — [Using `@Autowired`, 6.2](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired.html): nhiều bean ứng viên, thứ tự, quy tắc injection.
- Spring Framework — [Fine-tuning Annotation-based Autowiring with Qualifiers](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired-qualifiers.html): qualifier trên các injection point.
- Spring Framework — [`Order` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/core/annotation/Order.html): ý nghĩa annotation và hạn chế.
- Java SE 17 — [`Stream<T>` Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html): xử lý stream tuần tự, terminal operations và giới hạn tái sử dụng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="flexible-dependencies-008">Dùng jakarta.inject.Provider và chọn thời điểm lấy bean</a>

<details>
<summary>Xem chi tiết</summary>

# Dùng jakarta.inject.Provider và chọn thời điểm lấy bean trong Spring Core Container

## 1. Mục tiêu và bối cảnh

Trong Spring IoC Container, **dependency injection** thường giải quyết dependency ngay khi tạo bean nhận phụ thuộc. Đây là lựa chọn mặc định hợp lý cho dependency bắt buộc và ổn định. Tuy nhiên, đôi khi đối tượng cần dùng phải được lấy **vào đúng thời điểm xử lý nghiệp vụ**, chẳng hạn:

- Một singleton thực hiện nhiều công việc và mỗi công việc cần một bean `prototype` mới.
- Một dịch vụ chỉ chạm đến một bean có scope ngắn hơn khi có request đang hoạt động.
- Một dependency gây tốn chi phí hoặc liên quan đến vòng phụ thuộc, và việc truy cập nó cần được trì hoãn đến sau khi consumer đã được dựng.
- Một interface có nhiều implementation; mã sử dụng muốn nêu rõ implementation qua qualifier.

**`jakarta.inject.Provider<T>`** là một hợp đồng rất nhỏ trong Jakarta Dependency Injection: consumer nhận `Provider<T>` qua dependency injection, lưu provider và gọi `get()` khi cần đối tượng `T`. Spring Framework hỗ trợ hợp đồng này trong Core Container. Khái niệm cốt lõi là **trì hoãn việc lấy bean từ container**, không phải tự động tạo mới hoặc trì hoãn mọi khởi tạo trong toàn ứng dụng.

Sau bài này, bạn có thể giải thích cơ chế `Provider<T>`, tích hợp `jakarta.inject-api`, chọn thời điểm gọi `get()`, giải thích quan hệ với scope và kiểm thử các tình huống thiếu/nhiều bean.

## 2. Interface Provider<T> thực sự cung cấp gì?

`jakarta.inject.Provider<T>` có API tối giản:

```java
package jakarta.inject;

public interface Provider<T> {
    T get();
}
```

Đây là phần cốt lõi của interface được minh họa để học API. `T` là kiểu đối tượng mà injector/container cung cấp. Spring tạo và tiêm implementation của provider phù hợp với injection point. Bên sử dụng không phải viết implementation `Provider<T>` hoặc nhận trực tiếp `ApplicationContext`.

- `get()` **yêu cầu container cung cấp một đối tượng hoàn chỉnh và đã được inject** tại thời điểm gọi.
- Đối tượng là bean nào và có được tạo mới hay không phụ thuộc vào **bean definition, scope, qualifier và quy tắc chọn ứng viên**.
- `get()` là lookup **bắt buộc**: nếu Spring không tìm được bean hợp lệ hoặc gặp lỗi tạo bean, lời gọi có thể ném ngoại lệ runtime.
- Provider tiêu chuẩn **không** có `getIfAvailable()`, `getIfUnique()`, `stream()`, `orderedStream()` hoặc `getObject(args)`. Những khả năng này thuộc API mở rộng của Spring, như `ObjectProvider<T>`.

Trong Spring Framework 6+ và ứng dụng Spring Boot 3+, import đúng là:

```java
import jakarta.inject.Provider;
import jakarta.inject.Inject;
import jakarta.inject.Named;
```

Đừng vô tình import `javax.inject.Provider` từ hệ sinh thái Java EE/Spring đời cũ. Hai tên package là hai kiểu Java khác nhau và cần dependency tương ứng; bài này dùng namespace **`jakarta.inject`**.

### Cách hoạt động ở hai thời điểm

**Lúc tạo consumer:**

```text
Spring Container
    |
    +-- tạo WorkService
    |     |
    |     +-- inject Provider<Attempt> -- lưu provider
    |
    +-- WorkService sẵn sàng nhận tác vụ
```

**Lúc xử lý công việc:**

```text
WorkService.process()
    |
    +-- attemptProvider.get()
            |
            +-- Spring tìm bean Attempt phù hợp
            +-- áp dụng scope của Attempt
            +-- tạo hoặc tìm instance
            +-- trả instance cho lần xử lý hiện tại
```

Sơ đồ diễn giải đường đi của lookup. Nó không khẳng định bean singleton đích luôn được tạo lần đầu tại `get()`: một singleton không `@Lazy` có thể đã khởi tạo từ khi container refresh.

## 3. Vì sao cần chọn thời điểm lấy bean?

Giả sử `TaskRunner` là **singleton**, còn `Attempt` là **prototype**, mỗi instance lưu trạng thái riêng của một lần chạy.

### 3.1. Tiêm thẳng prototype vào singleton

```java
// Ví dụ cấu trúc; Attempt và TaskRunner là kiểu nghiệp vụ minh họa.
public final class TaskRunner {
    private final Attempt attempt;

    public TaskRunner(Attempt attempt) {
        this.attempt = attempt;
    }

    public void run() {
        attempt.execute();
    }
}
```

Nếu `TaskRunner` được tạo một lần, Spring giải quyết `Attempt` để cấp cho constructor **một lần**. Vì `TaskRunner` giữ nguyên tham chiếu, mỗi lần `run()` sẽ sử dụng chính đối tượng được tiêm trước đó. Annotation `@Scope("prototype")` của `Attempt` không có nghĩa Spring tự thay field ở các lần gọi tiếp theo.

### 3.2. Tiêm Provider rồi gọi get() ngay trong run()

```java
import jakarta.inject.Provider;

public final class TaskRunner {
    private final Provider<Attempt> attempts;

    public TaskRunner(Provider<Attempt> attempts) {
        this.attempts = attempts;
    }

    public void run() {
        Attempt current = attempts.get();
        current.execute();
    }
}
```

Đây cũng là đoạn minh họa cấu trúc, chưa là chương trình độc lập. Với `Attempt` scope `prototype`, **mỗi lời gọi `get()` giải quyết một bean prototype mới** theo quy tắc Spring. Nếu `Attempt` là singleton, các lần `get()` thường nhận cùng một đối tượng trong một `ApplicationContext`.

**Quyết định quan trọng:** lưu `Provider<Attempt>` trong consumer; chỉ lưu `Attempt` vào biến cục bộ khi bắt đầu một đơn vị công việc. Khi toàn bộ bước của một công việc phải dùng cùng attempt, gọi `get()` **một lần cho công việc đó** và truyền object cục bộ cho các bước. Đừng gọi `get()` lặp lại vô tình rồi tưởng các lời gọi chắc chắn cùng một attempt.

## 4. Thiết lập dự án Java 17+ với Spring Framework

Dưới đây là chương trình hoàn chỉnh dùng Spring Framework **6.2.19**, `jakarta.inject-api` **2.0.0**, Java **17+**. Ví dụ chủ ý chứng minh bốn điều: prototype được lấy muộn, scope singleton vẫn dùng lại instance, `@Named` chọn implementation giữa hai bean cùng interface, và `get()` phát sinh lỗi khi không có bean đích.

Tạo cấu trúc:

```text
provider-timing-demo/
├── settings.gradle
├── build.gradle
└── src/
    └── main/
        └── java/
            └── ProviderTimingDemo.java
```

**`settings.gradle`**

```groovy
rootProject.name = 'provider-timing-demo'
```

**`build.gradle`**

```groovy
plugins {
    id 'java'
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-context:6.2.19'
    implementation 'jakarta.inject:jakarta.inject-api:2.0.0'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass = 'ProviderTimingDemo'
}
```

`spring-context` cung cấp container và annotation cấu hình Spring. `jakarta.inject-api` cung cấp các kiểu `Provider`, `Inject` và `Named`. Trong một dự án Spring Boot có dependency management, version framework thường do BOM quản lý; khi đó không nên ép phiên bản Spring độc lập chỉ để thử provider.

**`src/main/java/ProviderTimingDemo.java`**

```java
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Named;
import jakarta.inject.Provider;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

public class ProviderTimingDemo {

    record Attempt(int id) {}

    record Settings(String mode) {}

    interface Formatter {
        String format(String input);
    }

    interface OptionalMetrics {
        void record();
    }

    static final class WorkService {
        private final Provider<Attempt> attempts;
        private final Provider<Formatter> formatter;
        private final Provider<Settings> settings;
        private final Provider<OptionalMetrics> metrics;

        WorkService(
                Provider<Attempt> attempts,
                Provider<Formatter> formatter,
                Provider<Settings> settings,
                Provider<OptionalMetrics> metrics) {
            this.attempts = attempts;
            this.formatter = formatter;
            this.settings = settings;
            this.metrics = metrics;
        }

        void process(String label) {
            Attempt current = attempts.get();
            String output = formatter.get().format(label);
            System.out.println("process=" + output + "#" + current.id());
        }

        boolean sameSettings() {
            return settings.get() == settings.get();
        }

        void probeOptionalMetrics() {
            try {
                metrics.get();
                System.out.println("metrics=present");
            } catch (NoSuchBeanDefinitionException ex) {
                System.out.println("metrics=absent");
            }
        }
    }

    @Configuration
    public static class Config {
        private final AtomicInteger sequence = new AtomicInteger();

        @Bean
        @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
        Attempt attempt() {
            int id = sequence.incrementAndGet();
            System.out.println("create-attempt-" + id);
            return new Attempt(id);
        }

        @Bean("mainFormatter")
        Formatter mainFormatter() {
            return input -> "main:" + input;
        }

        @Bean("backupFormatter")
        Formatter backupFormatter() {
            return input -> "backup:" + input;
        }

        @Bean
        Settings settings() {
            return new Settings("demo");
        }

        @Bean
        WorkService workService(
                Provider<Attempt> attempts,
                @Named("mainFormatter") Provider<Formatter> formatter,
                Provider<Settings> settings,
                Provider<OptionalMetrics> metrics) {
            return new WorkService(attempts, formatter, settings, metrics);
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(Config.class)) {
            WorkService work = context.getBean(WorkService.class);

            System.out.println("context-ready");
            System.out.println("before-first-get");
            work.process("A");
            work.process("B");
            System.out.println("singleton-same=" + work.sameSettings());
            work.probeOptionalMetrics();
        }
    }
}
```

Chạy trong thư mục `provider-timing-demo` với Gradle đã cài đặt:

```bash
gradle run --quiet
```

Nếu dự án có Gradle Wrapper, dùng `./gradlew run --quiet` trên Linux/macOS hoặc `gradlew.bat run --quiet` trên Windows. Mã cần Spring và Jakarta Inject trên classpath, nên lệnh `javac ProviderTimingDemo.java` đơn lẻ từ thư mục mã nguồn không đủ. Tác vụ `gradle run` sẽ biên dịch mã và cung cấp dependencies đã khai báo.

**Kết quả mà `main` in ra trên stdout:**

```text
context-ready
before-first-get
create-attempt-1
process=main:A#1
create-attempt-2
process=main:B#2
singleton-same=true
metrics=absent
```

### Diễn giải kết quả

1. `Config` và `WorkService` được dựng khi context bắt đầu; việc tiêm `Provider<Attempt>` chưa tạo `Attempt` vì `Attempt` là prototype chưa được yêu cầu.
2. `context-ready` và `before-first-get` xuất hiện **trước** `create-attempt-1`: đây là dấu hiệu thời điểm lấy bean do code nghiệp vụ quyết định.
3. `process("A")` gọi `attempts.get()` và tạo attempt 1. `process("B")` gọi `get()` lần nữa và tạo attempt 2.
4. Trong context có hai `Formatter`. Annotation `@Named("mainFormatter")` gắn trên tham số provider giúp Spring chọn đúng bean mang tên đó, nên output là `main:A` và `main:B`. Nếu bỏ qualifier mà không có quy tắc xác định ứng viên khác, lookup đơn của `Provider<Formatter>` có thể thất bại vì nhiều candidate.
5. `Settings` có scope mặc định `singleton`; hai lần `settings.get()` trỏ đến cùng instance nên `singleton-same=true`.
6. Không có bean `OptionalMetrics`. Tiêm `Provider<OptionalMetrics>` được, nhưng khi `get()` thực sự chạy, Spring báo `NoSuchBeanDefinitionException`; đoạn `catch` chuyển tình huống vắng bean đã được dự kiến thành dòng `metrics=absent`.

Các dòng do Gradle hoặc môi trường build in thêm, nếu có, nằm ngoài stdout chủ động của `main`.

**Lưu ý về ví dụ tùy chọn:** cách bắt `NoSuchBeanDefinitionException` chỉ để chỉ ra semantics của `Provider.get()`. Trong code thật, nếu dependency có thể vắng mặt theo thiết kế và bạn đang dùng Spring, `ObjectProvider<T>.getIfAvailable()` hoặc `Optional<T>` thường diễn đạt hợp đồng optional rõ ràng hơn. Đừng bắt mọi `RuntimeException` và coi đó là “bean không tồn tại”, vì có thể đang che lỗi tạo bean hoặc lỗi cấu hình.

## 5. Chọn thời điểm get(): quyền kiểm soát thuộc consumer

### 5.1. Lấy tại ranh giới một tác vụ

```java
void handle(Order order) {
    ProcessingSession session = sessionProvider.get();
    session.validate(order);
    session.reserve(order);
    session.finish();
}
```

`ProcessingSession` và `Order` là các kiểu nghiệp vụ minh họa, provider đã được inject trước đó. Chỉ gọi `get()` khi `handle` bắt đầu. Cả ba bước cùng một đối tượng `session`, điều này đặc biệt quan trọng nếu bean prototype giữ trạng thái giao dịch nghiệp vụ nội bộ.

Nếu thay ba lần dùng `session` bằng ba lần gọi `sessionProvider.get()`, prototype có thể tạo ba phiên làm việc riêng: hệ quả này thường sai khi một tác vụ cần cùng trạng thái.

### 5.2. Chỉ lookup trên nhánh thực sự cần

```java
void handle(Request request) {
    if (request.requiresExport()) {
        ExportWorker worker = exportWorkers.get();
        worker.export(request);
    }
}
```

Giả định `Request`, `ExportWorker` là kiểu nghiệp vụ và `exportWorkers` là `Provider<ExportWorker>` đã inject. Nhánh không xuất dữ liệu sẽ không gọi provider. Đây là **trì hoãn truy cập từ phía consumer**; nó không bảo đảm singleton `ExportWorker` chưa từng được Spring khởi tạo lúc khởi động.

### 5.3. Không lấy sớm ngay trong constructor

```java
public ReportService(Provider<ReportBuilder> builders) {
    this.builder = builders.get();
}
```

Đoạn trên khiến bean đích được giải quyết lúc tạo `ReportService`, đánh mất mục đích chọn thời điểm của consumer. Nếu muốn mỗi báo cáo có `ReportBuilder` mới, lưu provider:

```java
public ReportService(Provider<ReportBuilder> builders) {
    this.builders = builders;
}

public Report build(Input input) {
    return builders.get().build(input);
}
```

Hai đoạn là ví dụ riêng phần; `ReportService`, `ReportBuilder`, `Report` và `Input` tượng trưng cho kiểu nghiệp vụ, không phải một chương trình chạy độc lập. Với bản thực tế, khai báo đầy đủ field, kiểu và phương thức tương ứng.

### 5.4. Không giữ prototype quá lâu

Đối tượng prototype tạo ra cho một công việc có thể chứa trạng thái mutable và tài nguyên. Chọn ranh giới thời gian sống của nó theo công việc; đừng vô tình đưa object vừa lấy vào field singleton rồi giữ vĩnh viễn. Provider trì hoãn tạo/lookup, **không tự hủy các prototype khi dùng xong**. Nếu bean prototype giữ file/socket/connection, cần thiết kế đóng tài nguyên rõ ràng, chẳng hạn `try-with-resources` khi kiểu đối tượng hỗ trợ `AutoCloseable`.

## 6. Scope quyết định get() trả về gì

| Scope bean đích | Kết quả thông thường của hai lần `get()` | Ý nghĩa của việc chọn thời điểm |
| --- | --- | --- |
| `singleton` | Cùng instance trong một container | Hoãn lần truy cập từ consumer; singleton có thể đã được tạo khi container khởi động. |
| `prototype` | Hai instance mới khi giải quyết hai lookup | Mỗi công việc có thể xin một bean độc lập đúng thời điểm. |
| `request` | Cùng instance trong cùng HTTP request, khác instance ở request khác | Phải gọi khi request scope đang hoạt động và gắn đúng request. |
| `session` | Dùng lại instance trong cùng HTTP session, tách giữa session | Cần session context phù hợp trong ứng dụng web. |
| Scope tùy biến | Phụ thuộc chính sách scope đã đăng ký | Provider tuân thủ scope; bản thân nó không định nghĩa lifetime. |

Một `Provider<T>` có thể được tiêm vào singleton dù bean `T` thuộc scope ngắn hơn. Tuy nhiên, nếu gọi bean request-scoped ở thread không gắn request, lookup có thể thất bại vì **scope không hoạt động**. Đặc biệt, dùng `ExecutorService` hoặc `@Async` không có nghĩa request context tự được truyền sang worker thread.

Với Spring, bean khai báo bằng `@Component` hay `@Named` vẫn mặc định là **singleton** nếu không khai báo scope khác. Đừng lấy mặc định scope của một DI framework khác rồi áp sang Spring. Để có prototype trong Spring:

```java
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("prototype")
public class OneUseProcessor {
    public String run(String value) {
        return value.toUpperCase(java.util.Locale.ROOT);
    }
}
```

Đây là class Java hợp lệ khi có dependency Spring nhưng không chứa `main`. Có thể tiêm `Provider<OneUseProcessor>` vào consumer để lấy processor mỗi lần cần. Việc lựa chọn scope ở **bean đích**, không phải do đổi khai báo provider.

## 7. Kết hợp @Inject, @Named và qualifier

### 7.1. @Inject có thể dùng để tiêm Provider

```java
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.springframework.stereotype.Component;

@Component
public final class ExportService {
    private final Provider<OneUseProcessor> processors;

    @Inject
    public ExportService(Provider<OneUseProcessor> processors) {
        this.processors = processors;
    }

    public String convert(String source) {
        return processors.get().run(source);
    }
}
```

Ví dụ này kết hợp với `OneUseProcessor` ở mục trước là các bean component có thể được Spring scan. `@Inject` đánh dấu constructor được tiêm; `@Autowired` của Spring cũng có thể sử dụng. Với một constructor duy nhất, Spring còn hỗ trợ constructor injection không cần annotation, nhưng dùng `@Inject` có ích khi bạn muốn thể hiện rõ hợp đồng Jakarta DI.

### 7.2. Chọn implementation bằng @Named ở điểm tiêm

Giả sử có hai bean cùng kiểu `MessageTransport`:

```java
@Bean("smtpTransport")
MessageTransport smtpTransport() {
    return new SmtpTransport();
}

@Bean("localTransport")
MessageTransport localTransport() {
    return new LocalTransport();
}
```

`MessageTransport` và hai implementation ở trên là kiểu minh họa, cần tự khai báo trong ứng dụng. Để chọn một bean cụ thể ở thời điểm `get()`:

```java
public NotificationService(
        @Named("smtpTransport") Provider<MessageTransport> transports) {
    this.transports = transports;
}
```

Trong class hoàn chỉnh, `NotificationService` có field `Provider<MessageTransport> transports` tương ứng. `@Named("smtpTransport")` trên **tham số provider** tạo điều kiện chọn ứng viên theo tên trong Spring. Nếu không có qualifier và nhiều bean cùng kiểu vẫn ngang nhau, `transports.get()` có thể ném `NoUniqueBeanDefinitionException`.

Nếu một bean được đánh dấu `@Primary`, Spring có thể chọn nó cho dependency theo kiểu khi không có qualifier cụ thể. Tuy nhiên, nên dùng qualifier rõ ràng khi nghiệp vụ thực sự yêu cầu một implementation xác định; đừng dựa vào sự trùng hợp của tên hoặc số lượng bean.

### 7.3. @Named trên bean và @Named tại injection point là hai vai trò khác nhau

```java
import jakarta.inject.Named;

@Named("csvExporter")
public final class CsvExporter implements Exporter {
    // Các phương thức Exporter được hiện thực ở đây.
}
```

Đây là phác họa class: `Exporter` là interface nghiệp vụ chưa được khai báo. `@Named` trên class làm bean có thể được nhận diện qua component scanning và tên được đặt; còn `@Named` trên tham số provider giúp thu hẹp ứng viên khi lấy bean. Không nhầm hai vai trò, dù cùng tên annotation.

## 8. Thiếu bean, nhiều bean và thời điểm phát sinh lỗi

Tiêm `Provider<T>` thành công không đảm bảo `get()` sau này sẽ thành công. Provider là một **cổng yêu cầu**; container chỉ giải quyết đối tượng đích khi cổng được gọi.

| Trạng thái lúc lookup | Kết quả `Provider<T>.get()` thường gặp trong Spring |
| --- | --- |
| Đúng một ứng viên hợp lệ | Trả bean theo scope của ứng viên đó. |
| Không có ứng viên | `NoSuchBeanDefinitionException`. |
| Nhiều ứng viên không phân định được | `NoUniqueBeanDefinitionException` (một dạng `NoSuchBeanDefinitionException`). |
| Bean có ứng viên nhưng khởi tạo thất bại | Ngoại lệ khởi tạo bean, như `BeanCreationException` hoặc ngoại lệ runtime liên quan. |
| Scope không hoạt động | Ngoại lệ scope, ví dụ `ScopeNotActiveException` với scope phù hợp. |

**Điểm rất dễ sai:** `NoUniqueBeanDefinitionException` là subclass của `NoSuchBeanDefinitionException` trong Spring. Vì vậy, đoạn code demo bắt `NoSuchBeanDefinitionException` có thể bắt cả trường hợp **không duy nhất**, không chỉ trường hợp thiếu hoàn toàn. Trong demo không đăng ký `OptionalMetrics` nên output `metrics=absent` đúng theo cấu hình đã biết; **không nên dùng kiểu catch đó trong ứng dụng thực để kết luận chắc chắn bean không tồn tại** khi có nhiều candidate.

Nếu cần tách hai tình huống trong Spring:

```java
try {
    TaskHandler handler = handlers.get();
    handler.handle();
} catch (org.springframework.beans.factory.NoUniqueBeanDefinitionException ex) {
    // Cấu hình nhiều bean mà không chọn được một ứng viên.
} catch (org.springframework.beans.factory.NoSuchBeanDefinitionException ex) {
    // Không có ứng viên phù hợp còn lại.
}
```

Đoạn minh họa giả định `handlers` đã inject `Provider<TaskHandler>` và `TaskHandler` có `handle()`. Cần bắt subclass **trước** superclass. Trong hệ thống thật, thường tốt hơn là đảm bảo cấu hình hợp lệ, báo lỗi rõ ràng và chỉ dùng optional khi yêu cầu nghiệp vụ thực sự cho phép.

### Khi dependency là optional

`Provider#get()` không có phương thức “nếu có thì lấy, không có thì trả null”. Nếu sử dụng Spring, có hai hướng rõ ràng hơn:

```java
// Cách 1: lookup tùy chọn tại thời điểm sử dụng.
org.springframework.beans.factory.ObjectProvider<Feature> features =
        /* được Spring inject */;
Feature feature = features.getIfAvailable();

// Cách 2: optional khi xây dựng consumer.
java.util.Optional<Feature> featureAtInjection =
        /* được Spring inject */;
```

Đây là snippet giới thiệu kiểu API, không phải biểu thức khởi tạo standalone. `ObjectProvider<T>` là API Spring mở rộng và phù hợp khi cần lookup optional nhiều lần. `Optional<T>` biểu đạt tình trạng có/không **khi injection**, không cung cấp lời gọi lookup mới ở những thời điểm tiếp theo. Tại nơi cần dependency bắt buộc, giữ `Provider.get()` để lỗi cấu hình được bộc lộ.

**Không dùng:** `provider.get() == null` như chiến lược optional chung, vì thiếu bean trong Spring có thể gây exception ngay trước phép so sánh.

## 9. Provider và vòng phụ thuộc

Một lý do hợp lệ để dùng provider là tách **thời điểm xây dựng bean** khỏi **thời điểm sử dụng bean kia**. Ví dụ luồng cấu trúc:

```text
A tạo trước
  └─ có Provider<B> nhưng chưa gọi get()
B tạo sau
  └─ constructor cần A đã tạo xong
A.callB() khi runtime
  └─ provider.get() -> B
```

Nếu cả `A` và `B` tiêm trực tiếp qua constructor, container có thể không giải quyết được vòng khởi tạo. Thay một cạnh bằng `Provider<B>` có thể giúp cả hai được tạo nếu **`A` không gọi `get()` trong constructor, `@PostConstruct` hoặc quá sớm khi graph đang được xây**.

Đoạn minh họa:

```java
@Component
class A {
    private final Provider<B> bs;

    @Inject
    A(Provider<B> bs) {
        this.bs = bs; // chưa gọi get()
    }

    String callB() {
        return bs.get().message();
    }
}

@Component
class B {
    private final A a;

    @Inject
    B(A a) {
        this.a = a;
    }

    String message() {
        return "ready";
    }
}
```

Cần import `jakarta.inject.Inject`, `jakarta.inject.Provider` và `org.springframework.stereotype.Component` để đoạn trên biên dịch trong một package. Hai class nên nằm trong package được scan. `B` nhận `A` để thể hiện cạnh phụ thuộc vòng; phương thức `message()` không gọi lại `A.callB()`, tránh recursion trong runtime.

**Giới hạn:** Provider không chữa mọi circular dependency. Nếu `A` gọi `bs.get()` trong constructor khi `B` chưa thể được tạo, vòng khởi tạo vẫn có thể thất bại. Nếu `B.message()` lập tức gọi `A.callB()`, chương trình có nguy cơ lặp đệ quy khi runtime. Cân nhắc tách trách nhiệm hoặc dùng sự kiện, service trung gian thay vì giữ thiết kế vòng quá phức tạp.

## 10. Provider so với các API khác

| Cách nhận dependency | Thời điểm giải quyết | API nổi bật | Trường hợp phù hợp |
| --- | --- | --- | --- |
| Constructor injection `T` | Lúc tạo consumer | Dùng `T` trực tiếp | Dependency bắt buộc, scope ổn định. |
| `jakarta.inject.Provider<T>` | Khi consumer gọi `get()` | `get()` | Lookup theo thời điểm với interface Jakarta DI đơn giản. |
| `ObjectFactory<T>` của Spring | Khi consumer gọi `getObject()` | `getObject()` | Tương tự Provider, sử dụng API Spring tối giản. |
| `ObjectProvider<T>` của Spring | Khi gọi method lookup/stream | `getObject()`, `getIfAvailable()`, `getIfUnique()`, `stream()` | Lookup optional hoặc nhiều bean, cần các tiện ích mở rộng Spring. |
| `@Lazy T` ở injection point | Khi proxy đích lần đầu được dùng | Dùng method của `T` qua proxy | Muốn giữ kiểu `T` nhưng trì hoãn giải quyết target; chú ý semantics proxy. |
| `Optional<T>` | Lúc inject consumer | `isPresent()`, `orElse()` | Optional được quyết định tại thời điểm tạo consumer. |
| `ApplicationContext.getBean(...)` | Khi gọi context | Lookup qua container API | Cơ sở hạ tầng thật sự cần thao tác container. |

**Không nhầm tên:** `Provider<T>` không phải `ObjectProvider<T>`; mặc dù Spring tích hợp cả hai, chúng là **hai interface khác nhau**. `jakarta.inject.Provider<T>` cũng không phải `jakarta.enterprise.inject.Instance<T>` của CDI: `Instance<T>` có thêm khả năng khám phá/lọc candidate trong môi trường CDI. Dùng đúng API của container đang chạy.

### Khi nên chọn Jakarta Provider

Bạn có thể chọn `Provider<T>` nếu chỉ cần `get()` và muốn code nghiệp vụ phụ thuộc vào **chuẩn Jakarta DI**, nhất là khi chia sẻ library với các môi trường hỗ trợ chuẩn này. Tuy nhiên, khác container có thể có chính sách scope, chọn bean và loại exception khác nhau; hợp đồng portable là `Provider.get()`, **không phải** toàn bộ chi tiết ngoại lệ Spring.

Nên chọn `ObjectProvider<T>` nếu nghiệp vụ thật sự cần trả `null` khi vắng bean, fallback, kiểm tra unique hoặc duyệt tập bean theo `stream()`/`orderedStream()`. Nên tiêm trực tiếp `T` nếu không có nhu cầu trì hoãn thực tế, vì cách đó bộc lộ lỗi dependency thiếu ngay khi khởi tạo.

## 11. Những nhầm lẫn phổ biến

### 11.1. “Mỗi lần get() là một object mới”

Sai với singleton. Provider gọi lại container; container áp dụng scope. Singleton thường cùng instance trong context, còn prototype mới cho mỗi lần giải quyết. Cần cấu hình scope bean đích cho đúng.

### 11.2. “Có Provider là bean sẽ lazy hoàn toàn”

Sai. Provider chỉ trì hoãn **lần lookup từ consumer**. Nếu target là singleton eager, container có thể đã tạo bean tại `refresh()`. Nếu mục tiêu là trì hoãn cả khởi tạo singleton, xem xét `@Lazy` ở bean definition hoặc cấu hình khác thích hợp, đồng thời kiểm tra xem có dependency khác khiến bean được tạo sớm không.

### 11.3. “Provider giúp dùng request bean từ thread bất kỳ”

Sai. Request/session scope cần context hoạt động đúng. `get()` ngoài scope hợp lệ có thể báo lỗi. Đừng dựa vào provider để truyền thread-local request context qua background executor.

### 11.4. “Có Provider nên lúc khởi động không thể thiếu bean”

Provider làm thời điểm lỗi có thể trễ đến `get()`. Nếu bean bắt buộc nhưng chỉ dùng khi production traffic chạy, cấu hình sai có thể không xuất hiện ở startup. Hãy bổ sung integration test hoặc kiểm tra cấu hình ở nơi phù hợp để phát hiện sớm.

### 11.5. “Gọi get() trong constructor vẫn là lazy lookup”

Việc lấy target đã diễn ra trong constructor, tức là **sớm**. Giữ provider đến method xử lý hoặc ranh giới hoạt động phù hợp.

### 11.6. “Provider làm prototype tự được cleanup”

Sai. Spring cung cấp lifecycle khởi tạo cho prototype nhưng không tự quản lý toàn bộ callback destruction của prototype sau khi cấp bean. Đối tượng giữ tài nguyên phải có cơ chế đóng độc lập.

### 11.7. “Bắt NoSuchBeanDefinitionException nghĩa là chắc chắn zero bean”

Sai do `NoUniqueBeanDefinitionException` kế thừa `NoSuchBeanDefinitionException` trong Spring. Ngoại lệ phải được phân loại đúng, hoặc tốt hơn là dùng qualifier và hợp đồng dependency hợp lý để loại tình trạng mơ hồ.

### 11.8. “@Named trên Provider tự tạo bean theo tên”

Sai. Qualifier giúp **chọn trong những bean đã đăng ký**. Nếu không có bean tương ứng, `get()` vẫn thất bại. Muốn Spring quản lý bean, phải đăng ký bean bằng `@Bean`, component scanning hoặc cơ chế phù hợp.

### 11.9. “Provider thread-safe đồng nghĩa target thread-safe”

Container xử lý lookup và scope; nó không biến mọi bean nhận về thành thread-safe. Một singleton mutable vẫn cần thiết kế concurrency riêng. Một prototype chỉ giúp mỗi lookup nhận object khác, không có nghĩa object được dùng an toàn khi chia sẻ giữa thread.

## 12. Cách kiểm thử để xác nhận thời điểm lấy bean

Trong unit test của consumer thuần Java, có thể truyền provider giả **không cần Spring**:

```java
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.inject.Provider;

public final class ProviderUnitDemo {
    record Attempt(int id) {}

    static final class Runner {
        private final Provider<Attempt> attempts;

        Runner(Provider<Attempt> attempts) {
            this.attempts = attempts;
        }

        int runOnce() {
            return attempts.get().id();
        }
    }

    public static void main(String[] args) {
        AtomicInteger creations = new AtomicInteger();
        Provider<Attempt> fake = () -> new Attempt(creations.incrementAndGet());

        Runner runner = new Runner(fake);
        System.out.println("before=" + creations.get());
        System.out.println("run1=" + runner.runOnce());
        System.out.println("run2=" + runner.runOnce());
        System.out.println("after=" + creations.get());
    }
}
```

Ví dụ độc lập ở mức code Java nhưng vẫn cần `jakarta.inject-api` trên compile/runtime classpath. Nếu đặt file `ProviderUnitDemo.java` trong cùng Gradle project trên, có thể chạy bằng cách đổi tạm `application.mainClass` sang `ProviderUnitDemo` hoặc dùng một Gradle run task khác.

Output của chính `main` trong `ProviderUnitDemo`:

```text
before=0
run1=1
run2=2
after=2
```

Provider lambda giả cho phép kiểm tra **consumer gọi lookup lúc nào** bằng counter. Nó không kiểm tra Spring scope/qualifier, vì behavior lambda do test tự định nghĩa. Để xác nhận thực tế với Spring, tạo `AnnotationConfigApplicationContext` và test rằng prototype cho hai object khác nhau, singleton cho cùng một object, `@Named` chọn đúng bean và lookup thiếu dependency ném exception ở thời điểm gọi.

### Checklist trước khi đưa vào production

1. Xác định dependency thực sự phải lookup theo thời điểm hay có thể tiêm trực tiếp.
2. Chọn scope của target: `singleton`, `prototype`, request hoặc scope phù hợp.
3. Quyết định một công việc cần **một** hay **nhiều** lần lookup và giữ object đúng thời gian sống.
4. Nếu có nhiều implementation, thiết kế `@Named`/qualifier hoặc `@Primary` một cách rõ ràng.
5. Xác định dependency là bắt buộc hay optional; `Provider.get()` thuộc hợp đồng bắt buộc.
6. Kiểm tra behavior khi context/scope chưa sẵn sàng, ứng viên bị thiếu và khi factory tạo bean thất bại.
7. Kiểm soát cleanup của prototype có tài nguyên và tính thread-safe của các bean được chia sẻ.

## 13. Câu hỏi tự luyện và đáp án

**Câu 1.** Một singleton tiêm `Provider<Session>`; `Session` là prototype. Vì sao `get()` trong mỗi `handle()` hữu ích hơn so với `get()` trong constructor?

**Đáp:** Mỗi `handle()` lấy một instance prototype mới theo ranh giới công việc. Gọi trong constructor sẽ giải quyết một instance sớm và, nếu giữ trong field, tái sử dụng nó.

**Câu 2.** Hai lần `provider.get()` trả cùng đối tượng. Provider có hỏng không?

**Đáp:** Không. Bean đích có thể là singleton hoặc scope khác tái sử dụng object trong phạm vi đang hoạt động. Cần nhìn vào scope, không suy đoán từ tên API.

**Câu 3.** Vì sao code tiêm `Provider<Formatter>` có thể startup thành công nhưng lúc gọi `get()` lại báo nhiều bean?

**Đáp:** Provider có thể được tiêm trước khi Spring cần chọn một `Formatter` cụ thể. Tại lookup, nếu nhiều candidate không phân định được bằng qualifier/primary/quy tắc autowire, Spring báo không duy nhất.

**Câu 4.** Có thể dùng `@Named` ở đâu để chọn đúng implementation qua provider?

**Đáp:** Đặt `@Named("beanName")` trên injection point `Provider<Interface>`, và bảo đảm một bean phù hợp được đăng ký với tên ấy.

**Câu 5.** Trong ví dụ hoàn chỉnh, nếu đổi `Attempt` thành singleton, quan sát gì thay đổi?

**Đáp:** Container có thể tạo attempt ngay khi refresh trước dòng `context-ready`; hai lần `get()` sẽ nhận cùng instance, nên cả `process("A")` và `process("B")` dùng id bằng nhau. `get()` vẫn là lookup theo thời điểm gọi, nhưng không buộc container tạo object mới.

**Câu 6.** Nếu không có bean optional, nên luôn dùng `try/catch RuntimeException` quanh `Provider.get()`?

**Đáp:** Không. Cách đó che nhiều lỗi khác nhau. Nếu đang dùng Spring và dependency optional theo thiết kế, `ObjectProvider.getIfAvailable()` hoặc `Optional<T>` là API phù hợp hơn tùy yêu cầu thời điểm; nếu bắt buộc, hãy để lỗi cấu hình bộc lộ.

## 14. Tài liệu chuẩn để đối chiếu

- [Spring Framework 6.2 — Using JSR 330 Standard Annotations](https://docs.spring.io/spring-framework/reference/6.2/core/beans/standard-annotations.html): hỗ trợ `jakarta.inject.Provider`, `@Inject`, `@Named`, tương quan với `ObjectFactory`.
- [Spring Framework — Bean Scopes](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html): singleton, prototype và web scopes.
- [Spring Framework — Fine-tuning Autowiring with Qualifiers](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired-qualifiers.html): qualifier, bean name và các quy tắc lựa chọn ứng viên.
- [Jakarta Dependency Injection — Provider API](https://jakarta.ee/specifications/platform/10/apidocs/jakarta/inject/provider): hợp đồng `get()` và khả năng lookup theo scope.
- [Jakarta Inject — Inject API](https://javadoc.io/static/jakarta.inject/jakarta.inject-api/2.0.1/jakarta.inject/jakarta/inject/Inject.html): điểm tiêm và ngữ nghĩa `@Inject`.

**Ghi nhớ trọng tâm:** `Provider<T>` cho phép **consumer chọn lúc gọi `get()`**. Spring quyết định bean nào được cấp và tuổi thọ của nó dựa trên cấu hình IoC. Việc dùng đúng provider đòi hỏi phân biệt rạch ròi *thời điểm lookup*, *scope target* và *hợp đồng bắt buộc/tùy chọn*.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="flexible-dependencies-009">Giới hạn sử dụng provider để tránh che giấu phụ thuộc</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn sử dụng provider để tránh che giấu phụ thuộc trong Spring Core Container

## 1. Mục tiêu và vấn đề thiết kế

**`ObjectProvider<T>`** của Spring cho phép trì hoãn việc tra cứu bean và biểu diễn những trường hợp phụ thuộc có thể thiếu, có nhiều ứng viên hoặc cần lấy lại theo scope. Công cụ này hữu ích, nhưng nếu đặt provider ở khắp các lớp nghiệp vụ, dependency vốn bắt buộc có thể bị biến thành tra cứu ngầm. Ứng dụng có thể khởi động dù thiếu thành phần quan trọng, rồi chỉ thất bại khi chạy một nhánh nghiệp vụ, hoặc tệ hơn là lặng lẽ bỏ qua một hành động bắt buộc.

Ví dụ: một dịch vụ thanh toán **bắt buộc** cần `PaymentGateway` để thu tiền. Nếu thay constructor `PaymentGateway` bằng `ObjectProvider<PaymentGateway>` rồi gọi `getIfAvailable()` và bỏ qua khi trả `null`, lỗi cấu hình đã bị biến thành tình huống "không có gateway" được chấp nhận. Đó là **che giấu phụ thuộc**: chữ ký class không còn nói rõ chức năng không thể hoàn thành nếu thiếu gateway.

Bài học hướng tới việc dùng provider **có giới hạn và có chủ đích**:

- Nêu rõ khi nào một dependency là **bắt buộc**, **tùy chọn** hay **chỉ cần tra cứu khi chạy**.
- Phân biệt constructor injection với Service Locator; nhận biết việc provider bị lạm dụng như công cụ tìm bất cứ bean nào từ container.
- Giữ lỗi thiếu thành phần cốt lõi xuất hiện sớm (*fail fast*).
- Dùng `ObjectProvider<T>` phù hợp tại ranh giới tích hợp, factory theo scope, tính năng bổ trợ hoặc tập plugin.
- Hiểu chính xác `getObject()`, `getIfAvailable()`, `getIfUnique()`, `ifAvailable()`, `stream()` và `orderedStream()`.
- Viết ví dụ Java 17 + Spring Framework 6.2 chạy được và các kiểm thử cho trường hợp thiếu bean.
- Tránh coi lazy lookup là cách sửa vòng phụ thuộc, xử lý lỗi hoặc cấu hình thiếu.

**Nguyên tắc xuyên suốt:** Nếu một class **không thể hoàn thành cam kết nghiệp vụ** khi thiếu `T`, hãy khai báo `T` trực tiếp trong constructor. Chỉ đưa `ObjectProvider<T>` vào khi có một lý do đúng về thời điểm lấy, số lượng hoặc tính tùy chọn của bean.

## 2. Bản chất của `ObjectProvider<T>`

`ObjectProvider<T>` nằm trong `org.springframework.beans.factory`, được Spring ràng buộc với BeanFactory và kiểu `T` của injection point. Nó là một đối tượng cung cấp khả năng **yêu cầu bean khi cần**, không phải bản sao của bean `T`. Những lời gọi provider được phân giải trên trạng thái BeanFactory tại thời điểm gọi; kết quả còn phụ thuộc kiểu bean, qualifier, scope và các quy tắc chọn ứng viên.

Các phương thức thường gặp:

| Phương thức | Ý nghĩa cơ bản | Điều cần cảnh giác |
| --- | --- | --- |
| `getObject()` | Lấy một bean `T` bắt buộc | Thiếu bean gây `NoSuchBeanDefinitionException`; nhiều ứng viên không chọn được gây `NoUniqueBeanDefinitionException`; lỗi tạo bean vẫn có thể xảy ra |
| `getIfAvailable()` | Lấy một bean nếu có | Trả `null` khi không có ứng viên; **không tự giải quyết** nhập nhằng hoặc lỗi khởi tạo |
| `getIfAvailable(supplier)` | Lấy bean hoặc gọi supplier tạo mặc định khi thiếu | Fallback có thể che cấu hình sai nếu `T` thực ra bắt buộc |
| `ifAvailable(consumer)` | Chỉ chạy hành động nếu lấy được bean | Phù hợp tác vụ bổ trợ; không dùng để âm thầm bỏ qua bước bắt buộc |
| `getIfUnique()` | Lấy bean nếu phân giải được ứng viên duy nhất | Có thể trả `null` khi không có bean hoặc không có ứng viên thắng theo quy tắc chọn |
| `stream()` | Duyệt 0..n bean phù hợp | Không cam kết thứ tự |
| `orderedStream()` | Duyệt 0..n bean theo comparator của Spring | Chỉ phù hợp khi thứ tự nghiệp vụ được khai báo rõ và kiểm thử |

**Điểm dễ nhầm:** provider **không mặc định** làm mọi dependency trở thành tùy chọn. Nếu service gọi `getObject()` khi chạy, thiếu bean vẫn gây exception, chỉ là exception chuyển từ lúc khởi tạo service sang thời điểm thực thi. Nếu service gọi `getIfAvailable()` rồi bỏ qua `null`, nó có thể đã thay đổi hợp đồng nghiệp vụ thành "được phép thiếu".

Provider cũng không bảo đảm bean đích chưa được tạo: singleton mặc định có thể đã khởi tạo khi context khởi động. Provider là cơ chế tra cứu linh hoạt, không phải công tắc lazy tuyệt đối.

## 3. Thế nào là che giấu phụ thuộc?

Một constructor là nơi thể hiện rõ những thứ cần thiết để tạo một đối tượng hoạt động đúng. So sánh hai thiết kế.

### 3.1. Hợp đồng rõ ràng: dependency bắt buộc

```java
// Minh họa: PaymentGateway là interface của ứng dụng.
final class PaymentService {
    private final PaymentGateway gateway;

    PaymentService(PaymentGateway gateway) {
        this.gateway = gateway;
    }

    String pay(String orderId) {
        return gateway.charge(orderId);
    }
}
```

Service không tồn tại hợp lệ khi thiếu gateway. Khi Spring tạo bean `PaymentService` bằng constructor injection mà không tìm được bean `PaymentGateway`, quá trình tạo bean thất bại và nguyên nhân được báo tại khởi động context (thường được bọc thành `UnsatisfiedDependencyException`). Đây là tín hiệu cấu hình thiếu cần được sửa.

### 3.2. Phản ví dụ: provider được dùng như công cụ né lỗi

```java
// PHẢN VÍ DỤ minh họa: không dùng cho nghiệp vụ thanh toán thật.
final class HiddenPaymentService {
    private final ObjectProvider<PaymentGateway> gatewayProvider;

    HiddenPaymentService(ObjectProvider<PaymentGateway> gatewayProvider) {
        this.gatewayProvider = gatewayProvider;
    }

    String pay(String orderId) {
        PaymentGateway gateway = gatewayProvider.getIfAvailable();

        if (gateway == null) {
            return "SKIPPED:" + orderId; // Giả vờ hoàn tất luồng chính!
        }

        return gateway.charge(orderId);
    }
}
```

Đoạn này là minh họa thiết kế sai, cần khai báo `PaymentGateway` và import `ObjectProvider` trong dự án chứa nó; không phải chương trình độc lập. Vấn đề không nằm ở việc Spring cho phép `getIfAvailable()`; vấn đề là nghiệp vụ **đòi thanh toán** nhưng code coi thiếu gateway là điều hợp lệ. Hệ thống có thể khởi động, chạy qua nhiều lớp, và chỉ khi nhận đơn hàng mới phát hiện không thể thu tiền hoặc trả về trạng thái sai.

Ngay cả khi dùng:

```java
PaymentGateway gateway = gatewayProvider.getObject();
return gateway.charge(orderId);
```

thì dependency vẫn bị che đi phần nào: chữ ký `HiddenPaymentService(ObjectProvider<PaymentGateway>)` không nói trực tiếp nó **bắt buộc** cần một gateway, còn sự thiếu bean bị báo muộn lúc `pay` chạy. Khi không cần lazy lookup/scope, nhận `PaymentGateway` trực tiếp rõ ràng hơn.

### 3.3. Dấu hiệu lạm dụng provider

Hãy xem xét lại thiết kế nếu thấy một trong các dấu hiệu sau:

1. Hầu như mọi constructor đều nhận nhiều `ObjectProvider<...>` mặc dù các service hoạt động ngay với những dependency đó.
2. Nhiều method gọi `getIfAvailable()` hoặc `getIfUnique()` và âm thầm bỏ qua việc không lấy được bean.
3. Code gọi `ApplicationContext.getBean(...)` hoặc `BeanFactory.getBean(...)` bằng chuỗi tên class/bean thay vì khai báo dependency trong constructor.
4. Một service tự hỏi container xem nó có thể thanh toán, lưu dữ liệu hoặc kiểm soát quyền hay không, thay vì nhận thành phần bắt buộc cho các nhiệm vụ đó.
5. Unit test phải dựng Spring context thật chỉ để chạy một hàm nghiệp vụ thuần Java.
6. Thiếu bean chỉ được phát hiện khi người dùng gọi endpoint hiếm gặp hoặc khi tải tăng cao.
7. Provider xuất hiện để né một vòng phụ thuộc mà không thay đổi quan hệ trách nhiệm giữa các class.

Đây là **tín hiệu cần kiểm tra**, không phải lệnh cấm mọi provider. Một adapter thật sự cần tạo prototype bean theo từng request có lý do chính đáng để dùng provider ngay cả khi prototype đó là thành phần quan trọng.

## 4. Phân loại dependency trước khi chọn kiểu injection

Câu hỏi đầu tiên là: **Nếu bean này không tồn tại, chức năng có còn thực hiện đúng hợp đồng không?**

| Loại phụ thuộc | Ví dụ | Kiểu nên ưu tiên | Hành vi khi thiếu |
| --- | --- | --- | --- |
| Bắt buộc | `PaymentGateway` để thu tiền, repository để lưu dữ liệu | Constructor `PaymentGateway`/`Repository` | Lỗi cấu hình càng sớm càng tốt |
| Tùy chọn đơn lẻ | Adapter gửi telemetry/audit bổ trợ | `Optional<AuditSink>` nếu chỉ cần phân giải một lần | Bỏ qua hoặc dùng fallback được quy định rõ |
| Tùy chọn nhiều implementation | Các plugin ghi chỉ số, listener mở rộng | `List<Hook>` hoặc `ObjectProvider<Hook>.stream()` | Tập rỗng là trường hợp hợp lệ |
| Cần lazy lookup theo thời điểm | Một tác vụ chỉ kích hoạt plugin hiếm dùng | `ObjectProvider<Plugin>` tại ranh giới sử dụng | Phải quyết định rõ 0/1/n bean có nghĩa gì |
| Cần instance theo scope | Object `prototype` hoặc scoped bean cần lấy mỗi lần | `ObjectProvider<Worker>` hoặc factory chuyên dụng | Không suy đoán mỗi lần lấy đều là instance mới; xem scope |
| Cần chọn động theo nghiệp vụ | Chọn bộ xử lý theo tenant/mã kênh | Interface `StrategyRegistry` / factory hoặc map tường minh | Thiếu chiến lược phải có chính sách nghiệp vụ rõ |

**Đừng chọn kiểu chỉ để Spring khởi động được.** Chọn kiểu để thể hiện hợp đồng đúng. Nếu thật sự được phép thiếu một thành phần, hãy mô tả fallback, điều kiện kích hoạt và cách đo lường; nếu thành phần đó bắt buộc, giữ constructor injection tường minh.

Có thể dùng `@Qualifier` để xác định implementation yêu cầu:

```java
// Đoạn minh họa trong bean do Spring quản lý:
OrderService(@Qualifier("primaryPaymentGateway") PaymentGateway gateway) {
    this.gateway = gateway;
}
```

Điều này vẫn thể hiện **dependency bắt buộc**, đồng thời chọn đúng bean mong muốn. `@Primary` giải quyết một số tình huống nhiều ứng viên, nhưng không thay thế thiết kế hợp đồng.

## 5. Ví dụ hoàn chỉnh: cốt lõi bắt buộc, phần mở rộng tùy chọn

Ta xây một ứng dụng nhỏ sử dụng **constructor injection trực tiếp cho `PaymentGateway`**, và chỉ dùng provider ở **ranh giới các hook sau thanh toán**, vốn có thể không có hoặc có nhiều implementation.

Ứng dụng tạo hai Spring context riêng:

- Context chỉ có cấu hình lõi: gateway tồn tại, không có hook; thanh toán vẫn thành công.
- Context có cấu hình lõi và hook: thanh toán vẫn thực hiện, thêm hai hook phụ trợ theo thứ tự.

Mục tiêu là kiểm chứng provider được dùng để **mở rộng hành vi**, không phải hợp thức hóa việc thiếu thành phần chính.

### 5.1. Cấu trúc dự án

```text
bounded-provider/
├── pom.xml
└── src/main/java/demo/BoundedProviderDemo.java
```

Nội dung `pom.xml` (Spring Framework thuần, Java 17):

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>bounded-provider</artifactId>
    <version>1.0-SNAPSHOT</version>

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
```

Nội dung `src/main/java/demo/BoundedProviderDemo.java`:

```java
package demo;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

public class BoundedProviderDemo {

    interface PaymentGateway {
        String charge(String orderId);
    }

    interface PaymentHook {
        void afterPayment(String receipt);
    }

    static final class PaymentService {
        private final PaymentGateway gateway;

        PaymentService(PaymentGateway gateway) {
            this.gateway = gateway;
        }

        String pay(String orderId) {
            return gateway.charge(orderId);
        }
    }

    static final class CheckoutFacade {
        private final PaymentService payments;
        private final ObjectProvider<PaymentHook> hooks;

        CheckoutFacade(PaymentService payments,
                       ObjectProvider<PaymentHook> hooks) {
            this.payments = payments;
            this.hooks = hooks;
        }

        void checkout(String orderId) {
            String receipt = payments.pay(orderId);
            System.out.println("receipt=" + receipt);

            // Hook là phần mở rộng: không có hook vẫn hợp lệ.
            hooks.orderedStream().forEach(hook -> hook.afterPayment(receipt));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CoreConfig {

        @Bean
        PaymentGateway paymentGateway() {
            return orderId -> "PAID:" + orderId;
        }

        @Bean
        PaymentService paymentService(PaymentGateway gateway) {
            return new PaymentService(gateway);
        }

        @Bean
        CheckoutFacade checkoutFacade(
                PaymentService payments,
                ObjectProvider<PaymentHook> hooks) {
            return new CheckoutFacade(payments, hooks);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class OptionalHooksConfig {

        @Bean
        @Order(10)
        PaymentHook auditHook() {
            return receipt -> System.out.println("audit=" + receipt);
        }

        @Bean
        @Order(20)
        PaymentHook metricsHook() {
            return receipt -> System.out.println("metrics=" + receipt);
        }
    }

    public static void main(String[] args) {
        try (var context =
                     new AnnotationConfigApplicationContext(CoreConfig.class)) {
            System.out.println("core only:");
            context.getBean(CheckoutFacade.class).checkout("42");
        }

        try (var context = new AnnotationConfigApplicationContext(
                CoreConfig.class, OptionalHooksConfig.class)) {
            System.out.println("core plus hooks:");
            context.getBean(CheckoutFacade.class).checkout("42");
        }
    }
}
```

Chạy trong thư mục `bounded-provider` với JDK 17+ và Maven:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.BoundedProviderDemo
```

Các dòng do **chính chương trình** in ra (không tính log riêng của Maven hoặc môi trường):

```text
core only:
receipt=PAID:42
core plus hooks:
receipt=PAID:42
audit=PAID:42
metrics=PAID:42
```

### 5.2. Vì sao cách thiết kế này hợp lý?

**`PaymentService` nhận `PaymentGateway` trực tiếp.** Trong cả hai context, gateway là thành phần bắt buộc. Nếu bỏ bean `paymentGateway()` khỏi `CoreConfig` mà vẫn giữ `PaymentService`, context sẽ không tạo được service thành công do thiếu dependency. Điều đó đúng với yêu cầu "không thể thanh toán nếu không có gateway".

**`CheckoutFacade` nhận `ObjectProvider<PaymentHook>` tại ranh giới extension.** Số hook được phép là 0, 1 hoặc nhiều. Với context đầu tiên, `orderedStream()` rỗng nên không có output audit/metrics. Với context thứ hai, hai hook chạy theo `@Order(10)` rồi `@Order(20)`.

**Phạm vi provider có chủ đích.** Provider không được rải vào `PaymentService`, không được dùng để tìm `PaymentGateway` khi đến lệnh `pay`, và không thay đổi hợp đồng cốt lõi của nó. Lớp ranh giới `CheckoutFacade` điều phối phần mở rộng được phép vắng mặt.

**Hai context là hai cấu hình khởi động độc lập.** Ví dụ không tuyên bố rằng bạn có thể thêm bean động vào một ApplicationContext đã refresh rồi mọi provider sẽ tự hot-plug chúng trong mọi hoàn cảnh.

**Về xử lý lỗi hook:** Ví dụ không bắt exception từ hook. Nếu một hook ném exception sau khi gateway đã trả receipt, `checkout` sẽ gặp lỗi mặc dù thanh toán có thể đã hoàn tất. Trong ứng dụng thật, phải xác định chính sách lỗi, idempotency và thời điểm phát sự kiện (thí dụ sau khi transaction commit) trước khi để hook chạy đồng bộ; **provider không quyết định chính sách này**.

## 6. Chọn chính sách lỗi theo vai trò của dependency

Việc chọn `ObjectProvider` chỉ hợp lý khi consumer **chủ động xác định điều kiện nào được phép thiếu và điều kiện nào phải báo lỗi**. Thay vì học lại bảng API, hãy kiểm tra ba tình huống nghiệp vụ:

| Vai trò | Cách truy cập nên dùng | Khi thiếu hoặc nhập nhằng |
| --- | --- | --- |
| Gateway bắt buộc, dùng ngay | Constructor nhận `PaymentGateway` trực tiếp | Context báo thiếu/mơ hồ sớm; không trả thành công giả |
| Adapter phụ trợ tùy chọn duy nhất | `Optional<AuditSink>` hoặc `getIfAvailable()` nếu cần lookup muộn | Được phép thiếu, **nhập nhằng vẫn cần báo lỗi** |
| Extension point cho 0..n hook | `ObjectProvider<Hook>.stream()` / `orderedStream()` | Tập rỗng hợp lệ; nhiều hook phải được xử lý đủ |
| Chỉ bật tính năng nếu có lựa chọn duy nhất | `getIfUnique()` hoặc `ifUnique(...)` | Cả thiếu và mơ hồ đều bỏ qua **chỉ khi đó là chính sách đã được duyệt** |

`getIfAvailable(Supplier)` và `getIfUnique(Supplier)` không tương đương: fallback thứ nhất dành cho thiếu bean, fallback thứ hai còn áp dụng khi không chọn được ứng viên duy nhất. Một `@Primary` hợp lệ có thể giúp chọn một bean từ nhiều ứng viên; fallback không thay thế việc xử lý lỗi khởi tạo bean. Nếu fallback được tạo bằng `new`, nó không tự nhận injection, proxy và vòng đời từ Spring.

Trong ví dụ checkout, `PaymentService` nhận gateway trực tiếp để fail fast, còn `CheckoutFacade` duyệt toàn bộ hook ở ranh giới mở rộng. Đây là ranh giới thiết kế cần đánh giá; chi tiết exception và kết quả của từng phương thức đã được mô phỏng ở bài chuyên sâu về `getObject`/`getIfAvailable`/`getIfUnique`.

## 7. Bắt lỗi ở nơi đúng thay vì né phụ thuộc

Một mẫu dễ gây hiểu lầm:

```java
// PHẢN VÍ DỤ: không dùng cho nghiệp vụ bắt buộc.
try {
    repositoryProvider.getObject().save(order);
} catch (Exception ignored) {
    // Lặng lẽ bỏ qua mọi lỗi lưu dữ liệu.
}
```

Đoạn này có ít nhất ba vấn đề: dependency quan trọng được tìm muộn, lỗi thiếu bean bị trộn với lỗi lưu dữ liệu, và ngoại lệ bị nuốt không để lại dấu vết. Hệ thống có thể báo giao dịch thành công dù dữ liệu chưa được lưu.

Cách phù hợp hơn là **constructor injection trực tiếp** và xử lý lỗi nghiệp vụ bằng chính sách tường minh:

```java
// Minh họa trong lớp đã khai báo OrderRepository, Order và Receipt.
final class OrderApplicationService {
    private final OrderRepository repository;

    OrderApplicationService(OrderRepository repository) {
        this.repository = repository;
    }

    Receipt submit(Order order) {
        repository.save(order);
        return new Receipt(order.id());
    }
}
```

Đây là mã minh họa: `OrderRepository`, `Order` và `Receipt` là các kiểu do ứng dụng định nghĩa. Khi repository không đăng ký, Spring phát hiện thiếu dependency khi tạo bean. Khi `save` thất bại vì hạ tầng, exception đi ra đúng đường để transactional boundary, error handler và hệ thống quan sát xử lý. Dependency injection không thay cho chính sách transaction hoặc retry.

Nếu một thao tác thật sự "best effort" (ví dụ phát một metric không bắt buộc), có thể bắt lỗi **của thao tác phụ trợ**, log/đếm lỗi một cách phù hợp và xác định ảnh hưởng tới request. Nhưng nên phân biệt **không có plugin** với **plugin tồn tại nhưng chạy lỗi**; tự động coi hai tình huống như nhau làm khó vận hành.

## 8. Nơi provider được phép xuất hiện: ranh giới có chủ ý

Provider có giá trị ở những nơi nó thực sự giúp kiểm soát thời điểm hoặc số lượng bean.

### 8.1. Extension point có 0..n bean

```java
// Minh họa trong lớp ranh giới, không phải ứng dụng chạy độc lập.
final class HookDispatcher {
    private final ObjectProvider<DomainHook> hooks;

    HookDispatcher(ObjectProvider<DomainHook> hooks) {
        this.hooks = hooks;
    }

    void dispatch(Event event) {
        hooks.orderedStream().forEach(hook -> hook.onEvent(event));
    }
}
```

`DomainHook` và `Event` là kiểu ứng dụng. Nếu thiếu toàn bộ hook vẫn không ảnh hưởng nghiệp vụ, provider là hợp lý. Nếu cần đảm bảo luôn có một hook kiểm toán vì yêu cầu pháp lý, **đừng** tự coi stream rỗng là thành công; hãy kiểm tra điều kiện cấu hình hoặc khai báo thành phần bắt buộc.

### 8.2. Factory khi cần đối tượng prototype theo lượt

```java
// Minh họa: JobWorker là bean prototype trong cấu hình.
final class JobWorkerFactory {
    private final ObjectProvider<JobWorker> workers;

    JobWorkerFactory(ObjectProvider<JobWorker> workers) {
        this.workers = workers;
    }

    JobWorker newWorker() {
        return workers.getObject();
    }
}
```

Đây là ví dụ dùng provider để **lấy đúng thời điểm**, không để làm `JobWorker` trở thành tùy chọn. Khi thiếu bean, `getObject()` phát lỗi thay vì âm thầm tạo công việc giả. Có thể giấu chi tiết Spring phía sau một interface domain factory như `WorkerFactory` để các lớp nghiệp vụ khác không cần biết `ObjectProvider`.

Nếu bean có `@Scope("prototype")`, mỗi yêu cầu phân giải bean có thể tạo instance mới; nếu vẫn là singleton, provider thường trả singleton đó. Không suy luận scope chỉ từ việc code dùng provider.

### 8.3. Tích hợp tùy chọn một bean

Với dependency hiếm dùng, cho phép thiếu và chỉ phân giải **một lần** lúc tạo consumer, `Optional<T>` thường đơn giản hơn:

```java
// Đoạn minh họa trong một bean Spring-managed:
final class NotificationFacade {
    private final java.util.Optional<NotificationAdapter> adapter;

    NotificationFacade(java.util.Optional<NotificationAdapter> adapter) {
        this.adapter = adapter;
    }

    void notifyCreated(String id) {
        adapter.ifPresent(value -> value.send(id));
    }
}
```

Ngược lại, nếu muốn tra cứu khi được gọi hoặc phụ thuộc scope, provider có thể là lựa chọn phù hợp. Tránh dùng `ObjectProvider<T>` chỉ vì "linh hoạt hơn" khi thực tế không có nhu cầu linh hoạt nào.

### 8.4. Chọn implementation theo runtime key

Một service nhận mã phương thức vận chuyển và cần chọn đúng chiến lược có thể dùng **registry** thay vì tự quét BeanFactory trên mỗi nhánh:

```java
// Minh họa interface và consumer, không phải chương trình độc lập.
interface CarrierRegistry {
    Carrier forCode(String code);
}

final class ShippingService {
    private final CarrierRegistry registry;

    ShippingService(CarrierRegistry registry) {
        this.registry = registry;
    }

    void ship(String carrierCode, Parcel parcel) {
        registry.forCode(carrierCode).send(parcel);
    }
}
```

`Carrier` và `Parcel` là kiểu ứng dụng. Registry có thể nhận `Map<String, Carrier>`, `List<Carrier>` hoặc `ObjectProvider<Carrier>` ở **lớp hạ tầng** rồi áp dụng chính sách chọn và kiểm tra trùng khóa. Lớp `ShippingService` chỉ biết giao diện nghiệp vụ `CarrierRegistry`. Như vậy quyết định tra cứu bean tập trung ở một nơi dễ kiểm thử, không lan qua mọi service.

## 9. Service Locator và dependency ẩn

**Service Locator** là một kiểu thiết kế mà class tự yêu cầu một kho chung cung cấp dependency khi chạy, ví dụ lấy trực tiếp `ApplicationContext` rồi gọi `getBean("paymentGateway")` trong method.

```java
// PHẢN VÍ DỤ, minh họa mùi thiết kế.
final class LocatorCheckoutService {
    private final ApplicationContext context;

    LocatorCheckoutService(ApplicationContext context) {
        this.context = context;
    }

    String checkout(String id) {
        PaymentGateway gateway =
                context.getBean(PaymentGateway.class);
        return gateway.charge(id);
    }
}
```

Dù Spring hỗ trợ `ApplicationContext`, đây là ví dụ **trách nhiệm nghiệp vụ và cơ chế IoC bị trộn**. Constructor chỉ thấy `ApplicationContext`, không thấy `PaymentGateway` cần thiết. Lỗi cấu hình có thể bị đẩy đến thời điểm `checkout`. Unit test khó hơn vì phải mô phỏng container hoặc dùng Spring context.

`ObjectProvider<PaymentGateway>` tốt hơn locator tổng quát ở chỗ kiểu `PaymentGateway` còn xuất hiện trong chữ ký constructor, nhưng lạm dụng nó vẫn có thể tạo **lookup ngầm** và đẩy lỗi sang thời điểm chạy. Quy tắc thực hành:

- Đưa dependency bắt buộc vào constructor dưới dạng **interface cụ thể**.
- Nếu cần lazy/prototype, đặt provider trong một **factory hoặc adapter có trách nhiệm rõ**.
- Không truyền `ApplicationContext` xuyên qua nhiều tầng domain chỉ để "cần gì lấy nấy".
- Không giấu dependency bằng cách truyền `ObjectProvider<Object>` hoặc tìm bean theo chuỗi tên tùy ý.
- Nếu giới hạn cần thiết là "tất cả các plugin loại X", dùng `ObjectProvider<X>` đúng kiểu tại extension boundary; qualifier cũng có thể lọc nhóm.

Trong một số thành phần framework/infrastructure, truy cập `ApplicationContext` trực tiếp có thể hợp lý. Tiêu chí đánh giá là **mức độ phụ thuộc vào Spring bị lộ ra** và **độ rõ ràng của hợp đồng** của class, không phải cấm tuyệt đối một API.

## 10. Provider có giải quyết vòng phụ thuộc không?

Không nên dùng provider như cách né vòng phụ thuộc cấu trúc một cách vô điều kiện.

Giả sử `OrderService` phụ thuộc `InvoiceService` và `InvoiceService` lại phụ thuộc `OrderService`. Đổi một nhánh thành `ObjectProvider<OrderService>` có thể **trì hoãn** thời điểm yêu cầu bean, nhưng mối quan hệ trách nhiệm hai chiều vẫn tồn tại. Nếu gọi `provider.getObject()` ngay trong constructor hoặc khi hai bean còn đang khởi tạo, lỗi vòng phụ thuộc có thể vẫn xuất hiện.

Các hướng sửa thiết kế có chủ đích:

1. Tách trách nhiệm chung sang service thứ ba mà cả hai cùng phụ thuộc.
2. Tách hành động hậu xử lý qua sự kiện/domain event nếu nghiệp vụ cho phép.
3. Đưa một luồng điều phối vào orchestration service thay vì để hai service gọi qua lại.
4. Chỉ dùng provider khi cần đúng **thời điểm truy vấn** và vòng đời thực sự đòi hỏi nó; kiểm thử trường hợp bootstrap lẫn chạy thực tế.

`@Lazy` cũng là công cụ thay đổi thời điểm khởi tạo hoặc dereference trong những tình huống phù hợp. Nhưng thêm `@Lazy` không thay đổi sự thật một class đang cần dịch vụ nào, và không tự làm quan hệ hai chiều trở nên hợp lý về mặt kiến trúc.

## 11. Provider, scope và thời điểm phát hiện lỗi

Cùng một provider có thể mang rủi ro khác nhau tùy scope.

| Điều kiện | Khi gọi provider | Ý nghĩa thiết kế |
| --- | --- | --- |
| Singleton thường | Có thể trả bean đã được tạo sẵn | Provider không tự làm singleton lazy |
| Singleton `@Lazy` | Có thể tạo khi được yêu cầu lần đầu | Lỗi khởi tạo có thể đến muộn hơn |
| Prototype | Có thể tạo instance mới theo từng lần phân giải | Cần nhận thức về chi phí, vòng đời và cleanup resource |
| Scoped proxy/request scope | Có thể dùng proxy và resolve theo scope hiện hành | Gọi ngoài scope phù hợp có thể gây lỗi |
| Tập nhiều bean qua `stream()` | Resolve các ứng viên khi duyệt stream | Chỉ truy vấn khi cần, nhưng terminal operation có thể tạo nhiều bean |

Nếu nghiệp vụ bắt buộc phải có một adapter nhưng vì lý do hiệu năng bạn không muốn khởi tạo nó trước khi chạy tác vụ, có thể giữ provider trong factory/layer điều phối với tên method thể hiện **bắt buộc** (ví dụ `requiredWorker()` gọi `getObject()`). Điều quan trọng là **không biến thất bại thành "không có gì để làm"** và phải có kiểm thử cho nhánh hiếm dùng.

Nếu bean chỉ được yêu cầu theo một đường chạy đặc biệt (ví dụ job chạy cuối tuần), provider có thể khiến lỗi cấu hình chỉ lộ ra khi job được kích hoạt. Để khắc phục, bạn có thể thêm startup validation phù hợp hoặc một integration test kích hoạt đường này. Tránh cho rằng ứng dụng khởi động thành công nghĩa là mọi dependency được gọi động đều đã phân giải thành công.

## 12. Cân nhắc hiệu năng và tính quan sát

Provider không phải phương tiện tăng hiệu năng tự động. Mỗi lần gọi có thể cần bean resolution; một số lời gọi stream duyệt nhiều bean và có thể kích hoạt khởi tạo. Khi provider được gọi trong vòng lặp lớn hoặc request nóng, việc tra cứu liên tục có thể gây chi phí không cần thiết.

Có thể áp dụng:

- Nếu tập ứng viên cố định, inject `List<T>` hoặc thu snapshot `provider.orderedStream().toList()` một lần tại nơi phù hợp.
- Nếu cần tính động, chỉ gọi provider tại thời điểm thực sự cần; tránh gọi `getObject()` lặp lại mà không hiểu scope.
- Nếu hook chạy trên đường xử lý đồng bộ, xác định timeout, failure policy và trách nhiệm lỗi của từng hook; provider không tự cung cấp isolation/retry.
- Nếu "không có plugin" là trạng thái bình thường, ghi nhận qua log/metrics phù hợp ở tầng vận hành, nhưng không spam log trong mỗi request.
- Nếu "không có gateway" là sự cố cấu hình, để chương trình báo lỗi rõ; không đánh đồng với trạng thái tùy chọn.
- Đừng log thông tin nhạy cảm chỉ vì cần debug danh sách bean; xem metadata bean name là thông tin kỹ thuật, không phải dữ liệu nghiệp vụ.

**Chi phí test và maintainability** cũng là chi phí quan trọng. Một class có constructor `PaymentService(PaymentGateway gateway)` dễ test bằng một fake gateway; constructor `PaymentService(ApplicationContext context)` yêu cầu mock phức tạp hơn và người đọc phải dò phương thức nội bộ mới biết service thật sự dùng bean nào.

## 13. Kiểm thử để provider không che mất cấu hình sai

Một chiến lược test tốt phải kiểm tra **cả thiếu dependency hợp lệ lẫn thiếu dependency không hợp lệ**.

### 13.1. Thiếu optional hook phải thành công

Với `CoreConfig` ở ví dụ đầy đủ, context không đăng ký `PaymentHook` nào nhưng checkout vẫn trả receipt. Đây là một contract test cho extension point. Nếu một lần refactor làm code ném lỗi khi không có hook, test sẽ phát hiện.

### 13.2. Có nhiều hook phải chạy đúng thứ tự

Khi thêm `OptionalHooksConfig`, cả audit và metrics phải chạy. `@Order(10)` trước `@Order(20)`; test nên xác nhận **cả membership và thứ tự**, thay vì dựa vào thứ tự method khai báo hay component scan.

### 13.3. Thiếu gateway bắt buộc phải khiến context thất bại

Có thể thêm đoạn sau vào **cùng class `BoundedProviderDemo`** ở mục 5 để kiểm tra fail-fast bằng một configuration không khai báo gateway:

```java
// Đoạn bổ sung: khai báo bên trong BoundedProviderDemo.
@Configuration(proxyBeanMethods = false)
static class MissingGatewayConfig {
    @Bean
    PaymentService paymentService(PaymentGateway gateway) {
        return new PaymentService(gateway);
    }
}

static void verifyRequiredDependency() {
    try (var context = new AnnotationConfigApplicationContext(
            MissingGatewayConfig.class)) {
        throw new AssertionError("Context should reject missing gateway");
    } catch (org.springframework.beans.factory.UnsatisfiedDependencyException expected) {
        System.out.println("missing gateway rejected");
    }
}
```

Đây là **đoạn kiểm tra bổ sung**, không nằm trong output mặc định của `main()`. Muốn chạy, gọi `verifyRequiredDependency()` từ `main`. Nó cố tình tạo `PaymentService` mà không có `PaymentGateway`; khi Spring thất bại do thiếu dependency, ta bắt `UnsatisfiedDependencyException` và in:

```text
missing gateway rejected
```

Tùy logging configuration, Spring còn có thể ghi thông điệp chẩn đoán ra log. Đoạn thử nghiệm này chỉ hợp lệ khi context riêng thật sự **không đăng ký** bất kỳ `PaymentGateway` nào.

Trong unit test thuần Java, có thể tạo `PaymentService` bằng fake `PaymentGateway` và assert đầu ra; không cần Spring cho logic `pay`. Với `CheckoutFacade`, nên ưu tiên một abstraction như `PaymentHookDispatcher` nếu muốn unit test nghiệp vụ không phụ thuộc trực tiếp API Spring; integration test sẽ xác nhận adapter thực hiện lookup/orderedStream đúng hợp đồng.

### 13.4. Những trường hợp test không được bỏ sót

| Kịch bản | Kết quả kỳ vọng |
| --- | --- |
| Không có hook tùy chọn | Nghiệp vụ lõi hoàn tất, không gọi hook |
| Có một hook | Hook được gọi một lần tại thời điểm quy định |
| Có nhiều hook | Duyệt đầy đủ, đúng thứ tự/nhóm qualifier nếu có |
| Thiếu gateway bắt buộc | Context không tạo được service tương ứng |
| Có hai gateway cạnh tranh không phân giải được | Báo lỗi nhập nhằng, không âm thầm chọn ngẫu nhiên |
| Hook có bean nhưng khởi tạo thất bại | Lỗi được nhận diện là tạo bean thất bại, không giả làm "hook không tồn tại" |
| Hook ném lỗi khi chạy | Tác động tới checkout tuân theo chính sách lỗi đã thiết kế |
| Bean prototype/nhạy scope | Số lần tạo instance và thời điểm lookup được kiểm chứng |

Không cần mock mọi method của `ObjectProvider` để kiểm thử lớp nghiệp vụ. Với logic phức tạp, thường dễ hơn nếu giới hạn provider trong adapter và test adapter bằng Spring context nhỏ, còn service domain dùng interface thuần Java.

## 14. Checklist review mã nguồn

Khi review một constructor nhận `ObjectProvider<T>`, hãy trả lời:

1. **Vì sao cần provider?** Vì bean tùy chọn, vì có nhiều implementation, vì prototype/scope, vì lazy lookup, hay chỉ vì ngại khai báo dependency?
2. **Nếu không có bean `T` thì sao?** Chức năng chính có còn đúng không? Có fallback cụ thể, test và monitoring không?
3. **Nếu có nhiều bean `T` thì sao?** Cần tất cả, cần đúng một theo qualifier, hay cần registry theo khóa nghiệp vụ?
4. **Khi nào bean được lấy?** Lúc startup, lúc request, hay khi job chạy? Việc phát hiện lỗi bị trì hoãn đến mức nào?
5. **Nếu bean tạo lỗi hoặc chạy lỗi thì sao?** Code có phân biệt lỗi cấu hình/hạ tầng với vắng mặt hợp lệ không?
6. **Provider nằm ở lớp nào?** Ở adapter/factory/extension point hay lan vào domain service không cần biết Spring?
7. **Scope có được hiểu đúng không?** Singleton, prototype, request scope và lazy initialization có tạo ra hành vi mong muốn?
8. **Có thể dùng kiểu rõ hơn không?** `T` bắt buộc, `Optional<T>` tùy chọn một lần, `List<T>` nhóm cố định hoặc một interface factory/registry tường minh?
9. **Test có bao phủ thiếu/multiple bean không?** Đặc biệt kiểm thử rằng thiếu dependency cốt lõi phải gây lỗi.
10. **Người mới đọc constructor có hiểu service cần gì không?** Nếu phải vào từng method mới thấy service tìm gì từ context, thiết kế có thể đã ẩn dependency.

Checklist không đồng nghĩa "provider là xấu". Một provider được đặt đúng chỗ, có hợp đồng rõ, giới hạn phạm vi và kiểm thử hành vi thiếu bean là một công cụ hữu ích của Spring Core Container.

## 15. Lỗi thường gặp và cách sửa

| Biểu hiện | Sai lầm | Hướng khắc phục |
| --- | --- | --- |
| Ứng dụng khởi động nhưng thanh toán lỗi khi có đơn | `PaymentGateway` bắt buộc bị giấu trong provider | Constructor inject `PaymentGateway` trực tiếp |
| Thiếu bean mà code tiếp tục như thành công | `getIfAvailable()`/`ifAvailable()` bỏ qua hành động bắt buộc | Fail fast hoặc ném lỗi nghiệp vụ rõ ràng; kiểm thử trường hợp thiếu |
| `getIfAvailable()` vẫn ném exception | Có nhiều ứng viên không phân giải được hoặc bean tạo lỗi | Sửa qualifier/primary/bean factory; không giả định "if available" nuốt mọi lỗi |
| Không gọi đủ plugin | Dùng `getIfUnique()` cho extension point 0..n | Dùng `stream()`/`orderedStream()` khi cần mọi plugin |
| Unit test buộc khởi động Spring | Domain class giữ `ApplicationContext` và tự tra cứu bean | Constructor inject interface nghiệp vụ; giới hạn provider trong adapter/factory |
| Service A/B phụ thuộc vòng vẫn khó hiểu | Dùng provider chỉ để né lỗi circular dependency | Tách trách nhiệm, dùng orchestration/event khi phù hợp |
| Rule chạy sai thứ tự | Gọi `stream()` rồi phụ thuộc thứ tự đăng ký | Dùng `orderedStream()` và metadata `@Order` rõ |
| Tưởng dùng provider nghĩa là luôn tạo bean mới | Không xem bean scope | Phân biệt singleton/prototype/scoped proxy |
| Lỗi xuất hiện rất muộn | Provider trì hoãn lookup một dependency cốt lõi | Tiêm trực tiếp hoặc thêm startup/integration validation |
| Khó đo lỗi plugin | Bắt `Exception` và bỏ qua toàn bộ | Chính sách retry/fallback/log/metrics cụ thể theo loại hook |

## 16. Bài tập và đáp án định hướng

**Bài 1 — Chọn đúng kiểu phụ thuộc.** Một `ReportService` **bắt buộc** cần `ReportRepository` để lưu báo cáo, nhưng có thể gửi metric sang `MetricsSink` nếu bean này được đăng ký. Constructor nào diễn đạt hợp đồng tốt hơn?

```java
// Lựa chọn A
ReportService(ObjectProvider<ReportRepository> repository,
              ObjectProvider<MetricsSink> metrics) { }

// Lựa chọn B
ReportService(ReportRepository repository,
              java.util.Optional<MetricsSink> metrics) { }
```

**Đáp án:** B hợp lý khi metrics là tùy chọn được phân giải một lần. Repository là dependency bắt buộc nên constructor có thể phát hiện thiếu bean sớm. Nếu metrics thực sự cần lookup động/lazy, vẫn có thể dùng provider **chỉ cho metrics**.

**Bài 2 — Nhiều bean.** Nếu ứng dụng có ba `AuditHook` cùng kiểu và cần chạy cả ba, gọi `provider.getIfAvailable()` có đúng ý nghĩa không?

**Đáp án:** Không. Method này muốn trả một bean; khi nhiều ứng viên không chọn được duy nhất có thể ném exception. Với hook 0..n, dùng `stream()` hoặc `orderedStream()` tùy yêu cầu thứ tự.

**Bài 3 — Provider che lỗi.** Một service gọi `getIfUnique()` cho `FraudDetector`. Khi có hai detector không có ứng viên thắng, service nhận `null` rồi bỏ qua phát hiện gian lận. Lỗi nằm ở đâu?

**Đáp án:** Nếu phát hiện gian lận là bước bắt buộc, thiếu hoặc nhập nhằng detector phải được xem là sai cấu hình. Nên khai báo dependency bắt buộc, chọn đúng detector bằng qualifier/primary, hoặc thiết kế pipeline nhiều detector rõ ràng. Không dùng `null` làm "cho qua".

**Bài 4 — Prototype.** Mỗi job cần một `JobWorker` mới có state riêng. Có thể inject thẳng một `JobWorker` prototype vào singleton manager rồi gọi nhiều lần để luôn có worker mới không?

**Đáp án:** Không nên mặc định như vậy: dependency của singleton thường được phân giải lúc singleton được tạo, nên manager có thể giữ cùng một worker. Provider hoặc factory lấy prototype tại mỗi job phù hợp hơn, với scope được cấu hình đúng.

**Bài 5 — Ranh giới thiết kế.** Nếu mười service cùng giữ `ObjectProvider<PaymentGateway>` để gọi `getObject()` trước khi thu tiền, refactor như thế nào?

**Đáp án:** Trước hết xác định liệu mỗi service thật sự cần gateway. Chuyển dependency bắt buộc thành `PaymentGateway` hoặc một abstraction nghiệp vụ như `Payments` qua constructor. Nếu chỉ một adapter cần lazy/prototype semantics, giữ provider **bên trong adapter/factory đó**, cung cấp API thuần Java cho phần còn lại. Viết test xác nhận thiếu gateway là lỗi, không phải thành công.

## Kết luận

`ObjectProvider<T>` là công cụ **linh hoạt hóa thời điểm và cách phân giải bean**, không phải giấy phép biến mọi dependency thành tùy chọn. Giữ phụ thuộc bắt buộc dưới dạng constructor parameter kiểu `T` để lỗi cấu hình lộ ra sớm. Dùng provider cho extension point 0..n bean, lazy/prototype lookup hoặc factory/registry thật sự cần truy vấn động; giới hạn nó tại ranh giới hạ tầng có trách nhiệm rõ.

Trước khi thêm provider, hãy mô tả chính xác hành vi khi **không có**, **có một**, **có nhiều**, hoặc **có bean nhưng tạo/chạy lỗi**. Nếu những điều kiện đó chưa rõ, tăng mức "linh hoạt" bằng provider có thể làm giảm khả năng hiểu, kiểm thử và vận hành của hệ thống.

## Tài liệu tham khảo

- [Spring Framework — ObjectProvider API (6.2)](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/beans/factory/ObjectProvider.html): `getObject()`, `getIfAvailable()`, `getIfUnique()`, `stream()`, `orderedStream()` và các điều kiện chọn bean.
- [Spring Framework — Using @Autowired](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired.html): constructor injection, dependency bắt buộc và tùy chọn, collection injection.
- [Spring Framework — Fine-tuning Annotation-based Autowiring with Qualifiers](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired-qualifiers.html): cách ràng buộc ứng viên theo qualifier.
- [Spring Framework — Bean Scopes](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-scopes.html): singleton, prototype và các scope khác.
- [Spring Framework — Lazy-initialized Beans](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-lazy-init.html): sự khác nhau giữa lazy initialization và dependency resolution.


</details>

- [Quay lại đầu trang](#back-to-top)
