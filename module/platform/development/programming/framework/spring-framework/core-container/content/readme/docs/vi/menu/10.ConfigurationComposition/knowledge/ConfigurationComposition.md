<a id="back-to-top"></a>

# Ghép cấu hình

## Menu
- [Chia cấu hình theo ranh giới chức năng](#configuration-composition-001)
- [Nhập lớp cấu hình bằng @Import](#configuration-composition-002)
- [Kết hợp nhiều nguồn cấu hình trong một context](#configuration-composition-003)
- [Nhập cấu hình XML bằng @ImportResource](#configuration-composition-004)
- [Chọn cấu hình bằng ImportSelector](#configuration-composition-005)
- [Thời điểm xử lý DeferredImportSelector](#configuration-composition-006)
- [Đăng ký định nghĩa bằng ImportBeanDefinitionRegistrar](#configuration-composition-007)
- [Thiết kế annotation bật tính năng theo mẫu @Enable](#configuration-composition-008)
- [Tránh phụ thuộc vòng giữa các lớp cấu hình](#configuration-composition-009)

## <a id="configuration-composition-001">Chia cấu hình theo ranh giới chức năng</a>

<details>
<summary>Xem chi tiết</summary>

# Chia cấu hình theo ranh giới chức năng trong Spring Core Container

## 1. Vì sao cần chia cấu hình?

Một ứng dụng Spring nhỏ có thể khởi đầu bằng một class `@Configuration` chứa tất cả phương thức `@Bean`. Nhưng khi ứng dụng phát triển, một file cấu hình lớn thường trộn lẫn các trách nhiệm:

- Cấu hình lưu trữ: `DataSource`, repository, transaction infrastructure hoặc adapter dữ liệu.
- Cấu hình tính giá: discount policy, tax calculator và quy tắc nghiệp vụ.
- Cấu hình thanh toán: client của cổng thanh toán, retry policy và credential provider.
- Cấu hình checkout: use case kết hợp các phụ thuộc.
- Cấu hình giao tiếp: HTTP client, serialization và event publisher.

Nếu tất cả đặt trong một class `ApplicationConfig` dài hàng trăm hoặc hàng nghìn dòng, người đọc khó biết **bean nào thuộc chức năng nào, module nào phụ thuộc module nào, cấu hình nào cần cho test hoặc một môi trường triển khai cụ thể**.

Giải pháp là **chia cấu hình theo ranh giới chức năng**: mỗi nhóm bean được khai báo gần với trách nhiệm mà nó phục vụ, rồi một **composition root** ghép chúng thành application context. Trong Java configuration, `@Configuration`, `@Bean` và đặc biệt `@Import` cung cấp cơ chế trực tiếp cho cách làm này. Đây là bài về **ghép cấu hình** trong Spring Core Container, không phải quy định rằng mỗi module nhất thiết phải là microservice hoặc mỗi package phải có một container riêng.

### Mục tiêu học tập

Sau bài này, bạn có thể:

1. Nhận diện ranh giới cấu hình theo **capability/use case**, thay vì gom ngẫu nhiên theo loại annotation hoặc tiện ích.
2. Viết nhiều class `@Configuration` chuyên trách và sử dụng `@Import` để ghép chúng từ một điểm khởi động.
3. Nối dependency **xuyên cấu hình** bằng tham số `@Bean` để Spring resolve bean theo type, qualifier và scope.
4. Phân biệt `@Import`, `@ComponentScan`, `@Bean`, `@Profile` và `@Conditional`: mỗi công cụ đảm nhiệm một phần khác nhau.
5. Chọn điểm sở hữu bean hợp lý, tránh trùng tên, trùng kiểu, vòng phụ thuộc và việc khởi tạo tài nguyên lặp lại.
6. Thiết kế module dễ thay implementation và viết integration test theo phạm vi chức năng.
7. Chạy một ví dụ Java 17+ có các module **Storage**, **Pricing**, **Checkout**, được ghép mà **không cần component scanning**.

## 2. “Ranh giới chức năng” nghĩa là gì?

Ranh giới chức năng là nơi một nhóm cấu hình và bean phục vụ một **trách nhiệm tương đối độc lập**, có hợp đồng rõ với phần còn lại của hệ thống. Ví dụ:

| Ranh giới | Trách nhiệm | Bean tiêu biểu | Phụ thuộc công khai cần thiết |
| --- | --- | --- | --- |
| Storage | Lưu và tìm đơn hàng | `OrderStore`, adapter JDBC/in-memory | DataSource hoặc cấu hình storage |
| Pricing | Tính giá, khuyến mại | `DiscountPolicy` | Các chính sách giá, cấu hình tỷ lệ |
| Checkout | Điều phối việc đặt hàng | `CheckoutService` | `OrderStore` và `DiscountPolicy` |
| Integration | Kết nối dịch vụ bên ngoài | SDK client, timeout/retry | Settings và credentials |
| Delivery/Web | Chuyển HTTP request thành use case | Controller, message mapper | `CheckoutService` |

Một ranh giới hợp lý nên có:

- **Cohesion cao:** bean trong cùng nhóm phục vụ mục tiêu gần nhau.
- **Dependency direction rõ:** checkout có thể cần pricing và storage; pricing thường không cần biết checkout controller.
- **Hợp đồng ổn định:** module khác sử dụng interface hoặc public service thay vì cấu trúc bên trong.
- **Ownership nhất quán:** nhóm nào tạo bean, nhóm đó có trách nhiệm cấu hình scope/lifecycle phù hợp.
- **Khả năng bật/tắt/thay thế có chủ ý:** một adapter có thể đổi trong test mà không phải sửa hàng loạt cấu hình không liên quan.

Không có quy tắc “mỗi class một configuration” hay “mỗi module đúng một configuration”. Tách theo trách nhiệm thực sự, tránh cả **God Configuration** lẫn quá nhiều file không mang thêm ý nghĩa.

### Hai cách tổ chức package thường gặp

**Theo tầng kỹ thuật:**

```text
com.example
├── config
│   ├── DataConfig.java
│   ├── ServiceConfig.java
│   └── WebConfig.java
├── controller
├── service
└── repository
```

Cách này có thể tốt cho dự án nhỏ, nhưng khi nhiều chức năng khác nhau cùng chia sẻ một tầng, thay đổi một feature có thể kéo theo chỉnh sửa nhiều nơi.

**Theo chức năng/capability:**

```text
com.example
├── checkout
│   ├── CheckoutService.java
│   └── CheckoutConfiguration.java
├── pricing
│   ├── DiscountPolicy.java
│   └── PricingConfiguration.java
├── storage
│   ├── OrderStore.java
│   └── StorageConfiguration.java
└── bootstrap
    ├── ApplicationConfiguration.java
    └── Application.java
```

Đây là một phương án rõ ownership hơn khi các chức năng phát triển độc lập. Trong hệ thống lớn, có thể tiếp tục tách **API hợp đồng** khỏi implementation/infrastructure trong từng chức năng. Đừng cứng nhắc: lựa chọn phụ thuộc kích thước đội ngũ, hướng dependency, khả năng test và ranh giới triển khai thực tế.

## 3. Cơ chế Spring giúp ghép nhiều cấu hình

### 3.1. @Configuration mô tả một nhóm bean definitions

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PricingConfiguration {

    @Bean
    DiscountPolicy discountPolicy() {
        return new TenPercentDiscountPolicy();
    }
}
```

Đây là snippet minh họa, cần định nghĩa `DiscountPolicy` và `TenPercentDiscountPolicy` như ví dụ hoàn chỉnh ở phần sau. `@Configuration` cho Spring biết class chứa cấu hình; method `@Bean` đăng ký một bean definition. Khi context được dựng, Spring có thể tạo bean `discountPolicy` để inject vào các chức năng khác.

`proxyBeanMethods=false` phù hợp ở đây vì các factory methods độc lập, không cần gọi trực tiếp một `@Bean` method khác. **Nó không tắt dependency injection**: tham số của factory method vẫn được Spring giải quyết bình thường.

### 3.2. @Import ghép cấu hình theo danh sách tường minh

```java
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import({
    StorageConfiguration.class,
    PricingConfiguration.class,
    CheckoutConfiguration.class
})
public class ApplicationConfiguration {
}
```

Các class trong mảng phải có thật và được import đúng package. Đây là sơ đồ cấu hình sẽ xuất hiện trong ví dụ chạy được.

Khi `ApplicationConfiguration` được đăng ký vào `AnnotationConfigApplicationContext`, Spring xử lý `@Import` và các cấu hình được import. Các `@Bean` methods của chúng trở thành bean definitions trong **cùng ApplicationContext**, cho phép dependency injection xuyên ranh giới.

**Điểm quan trọng:** `@Import` không có nghĩa “nhúng source code Java”, không phải `new StorageConfiguration()` và không tự tạo context con. Nó là metadata để Spring nạp thêm các nguồn cấu hình/type vào context.

Tài liệu Spring còn cho phép `@Import` tham chiếu một số class component thông thường, `ImportSelector` hay `ImportBeanDefinitionRegistrar` trong các tình huống nâng cao. Trong bài này, dùng các class `@Configuration` tường minh là cách dễ học và dễ kiểm soát nhất.

### 3.3. Tham số @Bean nối dependency xuyên cấu hình

```java
@Configuration(proxyBeanMethods = false)
public class CheckoutConfiguration {
    @Bean
    CheckoutService checkoutService(
            OrderStore orderStore,
            DiscountPolicy discountPolicy) {
        return new CheckoutService(orderStore, discountPolicy);
    }
}
```

`CheckoutService`, `OrderStore` và `DiscountPolicy` là các kiểu trong ví dụ hoàn chỉnh. Spring giải quyết hai tham số từ bean definitions đã được đăng ký, dù chúng do **các configuration class khác** khai báo.

Đây là điểm mấu chốt của ghép cấu hình: **module checkout biết hợp đồng mà nó cần, không nhất thiết biết class cấu hình nào đã tạo implementation**.

Đừng thay cách này bằng:

```java
@Bean
CheckoutService checkoutService() {
    return new CheckoutService(
            new StorageConfiguration().orderStore(),
            new PricingConfiguration().discountPolicy()
    );
}
```

Đoạn phản ví dụ giả định truy cập các factory methods được phép ở package tương ứng. Tự `new` các configuration class và gọi trực tiếp factory methods có thể tạo object ngoài container, bỏ qua scope, lifecycle, proxies và sự thay thế bean trong test. Khi dùng `@Configuration(proxyBeanMethods=false)`, lời gọi chéo `@Bean` cũng mang semantics Java trực tiếp, không phải container lookup.

## 4. Vai trò của composition root

**Composition root** là nơi ứng dụng quyết định **những cấu hình/module nào cùng có mặt** trong một deployment. Thông thường là một hoặc một vài class gần entry point:

```text
                 ApplicationConfiguration
                       @Import
                      /   |   \
                     v    v    v
          StorageConfig PricingConfig CheckoutConfig
                |           |           |
                v           v           v
             OrderStore DiscountPolicy CheckoutService
                \           /
                 \         /
                  \       v
                    CheckoutService
```

Sơ đồ mô tả import ở lớp trên và dependency thật ở lớp dưới. `CheckoutService` nhận các hợp đồng do modules khác cung cấp; container không tự tạo dependency chỉ vì hai configuration cùng được import.

### Vì sao composition root hữu ích?

- **Một điểm quyết định thành phần ứng dụng:** startup không phải nhớ đăng ký thủ công hàng chục class cấu hình rời rạc.
- **Có thể cấu hình môi trường khác nhau:** test import storage in-memory; production import JDBC storage.
- **Dependency graph quan sát được:** composition root bộc lộ module nào tạo nên ứng dụng.
- **Ít scan ngoài ý muốn:** explicit import cho phép lựa chọn chính xác nhóm cấu hình được nạp.
- **Ranh giới trách nhiệm rõ:** pricing không cần tự import checkout để hoạt động; bootstrap làm nhiệm vụ ghép.

**Không nhầm:** `ApplicationContext` vẫn là **một** container trong ví dụ. Nhiều `@Configuration` không đồng nghĩa nhiều container hoặc nhiều singleton cache tách biệt. Scope singleton thông thường áp dụng trong context chung mà các definitions được ghép vào.

## 5. Ví dụ Java 17+ hoàn chỉnh: Storage + Pricing + Checkout

Ví dụ sau sử dụng **Spring Framework 6.2.19**, **Java 17+**, **Gradle** và `AnnotationConfigApplicationContext`. Ta cố ý không dùng `@ComponentScan` để nhìn rõ cơ chế **`@Import` + `@Bean` + parameter injection**.

Chương trình lưu dữ liệu giả lập trong bộ nhớ, tính giảm giá 10% bằng số nguyên và in kết quả xác định. Không cần database, web server, Spring Boot hoặc network.

### 5.1. Cấu trúc thư mục

```text
modular-config-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/
    ├── App.java
    ├── config/
    │   ├── ApplicationConfiguration.java
    │   ├── StorageConfiguration.java
    │   ├── PricingConfiguration.java
    │   └── CheckoutConfiguration.java
    ├── storage/
    │   ├── OrderStore.java
    │   └── InMemoryOrderStore.java
    ├── pricing/
    │   ├── DiscountPolicy.java
    │   └── TenPercentDiscountPolicy.java
    └── checkout/
        └── CheckoutService.java
```

Có **10 file Java**: một entry point, bốn configuration classes và năm class/interface triển khai hợp đồng. Mỗi file public top-level type đặt đúng tên và package tương ứng.

### 5.2. Cấu hình Gradle

**`settings.gradle`**

```groovy
rootProject.name = 'modular-config-demo'
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
    mainClass = 'demo.App'
}
```

Spring Framework 6.2.x hỗ trợ Java 17+. Dependency `spring-context` đem vào các thư viện Spring Core/Beans cần thiết qua Maven metadata. Không cần thêm `spring-boot-starter` để chạy demo IoC Container thuần.

### 5.3. Module Storage: hợp đồng và implementation

**`src/main/java/demo/storage/OrderStore.java`**

```java
package demo.storage;

public interface OrderStore {
    String save(String orderId, long chargedTotal);
    int count();
}
```

`OrderStore` là hợp đồng được Checkout sử dụng. Ứng dụng thực tế có thể thiết kế API domain phong phú hơn, nhưng demo chỉ cần lưu một record và đếm số lần ghi.

**`src/main/java/demo/storage/InMemoryOrderStore.java`**

```java
package demo.storage;

import java.util.concurrent.atomic.AtomicInteger;

public final class InMemoryOrderStore implements OrderStore {
    private final AtomicInteger writes = new AtomicInteger();

    @Override
    public String save(String orderId, long chargedTotal) {
        writes.incrementAndGet();
        return orderId + ":" + chargedTotal;
    }

    @Override
    public int count() {
        return writes.get();
    }
}
```

Implementation không có `@Component`. Chính `StorageConfiguration` sẽ chủ động đăng ký nó. `AtomicInteger` giúp bộ đếm an toàn trước việc tăng đồng thời, mặc dù demo chỉ chạy một thread. Đây là in-memory fake dùng để học cấu hình, **không phải persistence bền vững**.

### 5.4. Module Pricing: quy tắc tính giá

**`src/main/java/demo/pricing/DiscountPolicy.java`**

```java
package demo.pricing;

public interface DiscountPolicy {
    long discountedTotal(long baseTotal);
}
```

**`src/main/java/demo/pricing/TenPercentDiscountPolicy.java`**

```java
package demo.pricing;

public final class TenPercentDiscountPolicy implements DiscountPolicy {
    @Override
    public long discountedTotal(long baseTotal) {
        if (baseTotal < 0) {
            throw new IllegalArgumentException("negative amount");
        }
        return baseTotal - (baseTotal / 10);
    }
}
```

Đây là chính sách demo tính bằng `long`. Với 100, kết quả 90; với 50, kết quả 45. Ứng dụng tiền tệ thật nên định nghĩa đơn vị nhỏ nhất, rounding policy, overflow handling và dùng `BigDecimal` khi bài toán đòi hỏi, thay vì coi phép chia nguyên này là chuẩn nghiệp vụ phổ quát.

### 5.5. Module Checkout: chỉ phụ thuộc interface

**`src/main/java/demo/checkout/CheckoutService.java`**

```java
package demo.checkout;

import demo.pricing.DiscountPolicy;
import demo.storage.OrderStore;

import java.util.Objects;

public final class CheckoutService {
    private final OrderStore store;
    private final DiscountPolicy discounts;

    public CheckoutService(OrderStore store, DiscountPolicy discounts) {
        this.store = Objects.requireNonNull(store);
        this.discounts = Objects.requireNonNull(discounts);
    }

    public String checkout(String orderId, long baseTotal) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("blank order id");
        }
        long charged = discounts.discountedTotal(baseTotal);
        return store.save(orderId, charged);
    }
}
```

Checkout không import `StorageConfiguration`, `PricingConfiguration` hoặc các implementation cụ thể; nó chỉ dùng `OrderStore` và `DiscountPolicy`. Nhờ vậy, test nghiệp vụ có thể truyền fake vào constructor mà **không cần Spring**.

### 5.6. Ba configuration classes chuyên trách

**`src/main/java/demo/config/StorageConfiguration.java`**

```java
package demo.config;

import demo.storage.InMemoryOrderStore;
import demo.storage.OrderStore;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class StorageConfiguration {
    @Bean
    OrderStore orderStore() {
        return new InMemoryOrderStore();
    }
}
```

**`src/main/java/demo/config/PricingConfiguration.java`**

```java
package demo.config;

import demo.pricing.DiscountPolicy;
import demo.pricing.TenPercentDiscountPolicy;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PricingConfiguration {
    @Bean
    DiscountPolicy discountPolicy() {
        return new TenPercentDiscountPolicy();
    }
}
```

**`src/main/java/demo/config/CheckoutConfiguration.java`**

```java
package demo.config;

import demo.checkout.CheckoutService;
import demo.pricing.DiscountPolicy;
import demo.storage.OrderStore;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CheckoutConfiguration {
    @Bean
    CheckoutService checkoutService(
            OrderStore orderStore,
            DiscountPolicy discountPolicy) {
        return new CheckoutService(orderStore, discountPolicy);
    }
}
```

Điểm đáng chú ý: ba configuration class có trách nhiệm riêng. `CheckoutConfiguration` **không khởi tạo một repository hay discount policy thứ hai**. Spring sẽ resolve cả hai parameter từ container.

Trong các module có nhiều implementation `OrderStore` hoặc `DiscountPolicy`, cần khai báo qualifier hoặc `@Primary` phù hợp. Bài demo chỉ có một bean cho mỗi interface để luồng khởi đầu dễ kiểm chứng.

### 5.7. Composition root dùng @Import

**`src/main/java/demo/config/ApplicationConfiguration.java`**

```java
package demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import({
    StorageConfiguration.class,
    PricingConfiguration.class,
    CheckoutConfiguration.class
})
public class ApplicationConfiguration {
}
```

Đây là file quyết định ba ranh giới sẽ được ghép trong ứng dụng. Không cần `@ComponentScan`. Không cần ghi `@Import` trong từng module để import qua lại thành vòng.

Đặt entry point chỉ phụ thuộc `ApplicationConfiguration` là cách biểu đạt composition root rõ ràng.

### 5.8. Entry point

**`src/main/java/demo/App.java`**

```java
package demo;

import demo.checkout.CheckoutService;
import demo.config.ApplicationConfiguration;
import demo.storage.OrderStore;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public final class App {
    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(
                        ApplicationConfiguration.class)) {

            CheckoutService checkout =
                    context.getBean(CheckoutService.class);
            OrderStore store =
                    context.getBean(OrderStore.class);

            System.out.println("store-bean="
                    + context.containsBean("orderStore"));
            System.out.println("discount-bean="
                    + context.containsBean("discountPolicy"));

            System.out.println("checkout1="
                    + checkout.checkout("ORDER-7", 100));
            System.out.println("checkout2="
                    + checkout.checkout("ORDER-8", 50));

            System.out.println("writes=" + store.count());
            System.out.println("store-count="
                    + context.getBeansOfType(OrderStore.class).size());

            System.out.println("service-singleton="
                    + (checkout == context.getBean(CheckoutService.class)));
        }
    }
}
```

### 5.9. Chạy

Trong thư mục `modular-config-demo`, với Gradle và JDK 17+:

```bash
gradle run --quiet
```

Nếu có Gradle Wrapper, dùng `./gradlew run --quiet` trên Linux/macOS hoặc `gradlew.bat run --quiet` trên Windows. Lệnh `javac App.java` đơn lẻ không đủ vì project có nhiều file/package và cần Spring JAR trên classpath; Gradle xử lý compile/runtime dependencies.

**Các dòng do ứng dụng in ra:**

```text
store-bean=true
discount-bean=true
checkout1=ORDER-7:90
checkout2=ORDER-8:45
writes=2
store-count=1
service-singleton=true
```

Output có thể kèm log của Gradle hoặc runtime tùy môi trường, nhưng bảy dòng trên đến từ `System.out.println` của chương trình.

### 5.10. Vì sao output đúng?

1. `ApplicationConfiguration` được đăng ký trực tiếp. Spring phân tích `@Import` và đăng ký definitions từ ba class `@Configuration` được import.
2. `@Bean OrderStore orderStore()` tạo một `InMemoryOrderStore`; `containsBean("orderStore")` trả `true`.
3. `@Bean DiscountPolicy discountPolicy()` tạo `TenPercentDiscountPolicy`; bean tên `discountPolicy` tồn tại.
4. `CheckoutConfiguration` yêu cầu `OrderStore` và `DiscountPolicy` bằng tham số factory method. Hai interface này có mỗi loại đúng một bean hợp lệ, nên Spring inject được.
5. `checkout("ORDER-7", 100)` áp quy tắc giảm 10%, lưu `ORDER-7:90`. Lệnh thứ hai lưu `ORDER-8:45`.
6. `OrderStore` là singleton mặc định. Checkout và entry point đều dùng cùng managed store, nên sau hai lần lưu, `store.count()` bằng 2.
7. `getBeansOfType(OrderStore.class).size()` bằng 1 vì chỉ có một bean loại này trong context.
8. `CheckoutService` cũng mặc định singleton nên hai lần `getBean(CheckoutService.class)` trả cùng instance.

**Điểm cốt lõi được kiểm chứng:** nhiều configuration classes vẫn đóng góp bean vào **một IoC Container chung**, và các dependencies có thể nối xuyên class/package qua type. Không có lệnh `new StorageConfiguration()` trong checkout hoặc lời gọi trực tiếp `orderStore()` từ một configuration khác.

## 6. Vì sao nên tránh các cấu hình import qua lại?

Xem một tình huống tổ chức kém:

```text
PricingConfiguration -> @Import(CheckoutConfiguration)
CheckoutConfiguration -> @Import(StorageConfiguration)
StorageConfiguration -> @Import(PricingConfiguration)
```

Một số tổ hợp import qua lại có thể được Spring xử lý theo metadata trong các giới hạn cụ thể, nhưng **về mặt thiết kế**, chúng làm dependency direction mờ đi: lớp thấp hơn cần “biết” ứng dụng cao hơn và khó xác định composition root.

Đề xuất rõ ràng hơn:

```text
ApplicationConfiguration
    |
    +--> StorageConfiguration
    +--> PricingConfiguration
    +--> CheckoutConfiguration

CheckoutService
    |
    +--> OrderStore interface
    +--> DiscountPolicy interface
```

**Phân biệt hai loại đồ thị:**

- **Import graph** nói cấu hình nào được nạp vào application context.
- **Bean dependency graph** nói bean nào cần bean nào khi được tạo/sử dụng.

Không nên cố mô phỏng bean dependency graph bằng cách để mỗi module tự `@Import` những configuration của module mà nó dùng. Composition root nạp tập modules, còn bean dependency được thể hiện qua constructor/factory method parameters. Đây là cách giảm coupling giữa các configuration classes.

## 7. @Import và @ComponentScan: dùng khi nào?

| Tiêu chí | `@Import` | `@ComponentScan` |
| --- | --- | --- |
| Cách chọn class | Tường minh bằng class/type được tham chiếu | Tự tìm candidate trong base packages theo filters |
| Mục đích chính | Ghép cấu hình hoặc đăng ký type được chỉ định | Phát hiện `@Component` và các stereotype |
| Bị ảnh hưởng bởi package scan? | Không cần package scan của type được import | Có |
| Dễ thấy module nào được bật từ composition root? | Có, đọc mảng `@Import` | Phải xem package/filter và classpath |
| Có thể dùng với class không có `@Component`? | Có, theo các kiểu `@Import` hỗ trợ | Scanner mặc định không tự chọn type thiếu marker |
| Phù hợp | Tập cấu hình/feature module xác định rõ | Nhiều application components theo convention |

Không cần lựa chọn **chỉ một**. Một tổ chức thực tế thường:

1. Composition root dùng `@Import` để ghép các cấu hình chức năng.
2. Mỗi configuration có thể dùng `@ComponentScan(basePackageClasses=FeatureMarker.class)` để tìm service/controller của **chức năng đó**, hoặc dùng `@Bean` explicit.
3. Tránh scan package gốc quá rộng nếu dễ cuốn các test doubles, adapters và configuration không dự kiến vào context.

Ví dụ cấu trúc cho một feature có nhiều component:

```java
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = PricingPackageMarker.class)
public class PricingConfiguration {
}
```

`PricingPackageMarker` là marker class/interface nằm trong package muốn scan. `basePackageClasses` dùng **package** của class tham chiếu làm gốc; nó không chỉ quét riêng class marker. Cần import `ComponentScan`, `Configuration` và marker hợp lệ.

**Lưu ý:** nếu marker nằm ở một package quá chung, scanner có thể nhận cả cấu hình/bean của modules khác. Việc gắn package vào module chức năng là ý định tổ chức, không tự tạo “module isolation” ở runtime: tất cả bean được scan/import vẫn nằm trong context hiện tại.

## 8. Ranh giới chức năng không tự biến thành DI isolation

Khi import ba configuration vào một context, các bean definitions đều cùng tham gia dependency resolution trong context ấy. Spring không tự ngăn một bean Storage phụ thuộc ngược vào Checkout chỉ vì chúng ở package khác.

**Ví dụ hướng dependency kém:**

```text
Storage adapter ---> CheckoutService ---> OrderStore
        ^                                 |
        +---------------------------------+
```

Rủi ro là vòng phụ thuộc và kiến trúc khó thay thế. Ngược lại, nếu Checkout chỉ biết `OrderStore` interface, implementation có thể thay bằng JDBC, event store hoặc in-memory fake mà không chỉnh sửa CheckoutService.

Để enforce boundaries, có thể dùng:

- Code review và quy ước package.
- Module Java/Gradle/Maven phù hợp với kích thước dự án.
- Kiểm thử kiến trúc kiểm soát import directions.
- Chỉ export hợp đồng được tiêu thụ; implementation để internal khi Java visibility/package cho phép.
- Dependency graph review trước khi thêm một constructor dependency mới.

**Tách cấu hình là một bước tổ chức; không tự động là bảo đảm cô lập kiến trúc.**

## 9. Nên chia theo chức năng hay theo hạ tầng kỹ thuật?

Cả hai hướng có chỗ dùng.

### 9.1. Feature-first khi use case có ownership rõ

Một checkout feature có thể sở hữu `CheckoutConfiguration`, service và các adapter đặc thù. Mỗi feature module mang một “entry point” cấu hình và public contracts của nó.

Ưu điểm:

- Đội phụ trách feature dễ tìm nơi thay đổi.
- Test chỉ cần import configuration liên quan.
- Ít chỉnh sửa central config khi nội bộ feature phát triển.
- Có thể tắt/thay module bằng composition root.

Nhược điểm: nếu mỗi feature tạo một `ObjectMapper` hoặc một connection pool riêng dù chúng phải chia sẻ, hệ thống có thể trùng tài nguyên và khó quản lý lifecycle.

### 9.2. Infrastructure-first khi tài nguyên có ownership chung

Các tài nguyên như shared `DataSource`, Jackson mapper, HTTP connection pool, metrics hoặc clock có thể được định nghĩa ở một **shared infrastructure configuration**. Những feature khác inject **hợp đồng** của chúng, tránh tạo tài nguyên trùng.

Ví dụ:

```text
ApplicationConfiguration
├── SharedInfrastructureConfiguration
├── CustomerFeatureConfiguration
├── OrderFeatureConfiguration
└── BillingFeatureConfiguration
```

Nhưng một shared config cũng có thể trở thành “God Configuration” nếu mọi feature-specific decision bị nhét vào đó. Tiêu chí nên là **ai sở hữu lifecycle/policy của tài nguyên** và **những ai thật sự cần dùng chung**, không đơn thuần số lượng class.

### 9.3. Hybrid thường thực tế nhất

- Configuration theo feature/use case: service, policies, feature adapters.
- Configuration theo shared infrastructure: tài nguyên chung thực sự.
- Composition root: chọn tập modules cho một application/deployment.
- Profiles/conditions: chọn biến thể implementation theo môi trường khi cần.
- Interface: ngăn feature service lệ thuộc trực tiếp vendor SDK.

## 10. Thay đổi implementation mà không sửa service

Trong ví dụ, Checkout dùng `OrderStore`, không biết `InMemoryOrderStore`. Ta có thể định nghĩa một implementation khác:

```java
public final class JdbcOrderStore implements OrderStore {
    private final JdbcTemplate jdbc;

    public JdbcOrderStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String save(String id, long total) {
        jdbc.update(
            "insert into orders(id, total) values (?, ?)",
            id, total
        );
        return id + ":" + total;
    }

    @Override
    public int count() {
        Integer value = jdbc.queryForObject(
            "select count(*) from orders",
            Integer.class
        );
        return java.util.Objects.requireNonNull(value);
    }
}
```

Đây là **mã minh họa không độc lập**, vì cần import `OrderStore`, `JdbcTemplate` (`spring-jdbc`), một database với schema `orders`, cấu hình `DataSource` và các kiểu tương ứng. Không dùng SQL snippet này như một migration/schema production.

Khi production chọn JDBC:

```java
@Configuration(proxyBeanMethods = false)
class JdbcStorageConfiguration {
    @Bean
    OrderStore orderStore(JdbcTemplate jdbc) {
        return new JdbcOrderStore(jdbc);
    }
}
```

Composition root có thể import `JdbcStorageConfiguration` thay cho `StorageConfiguration` in-memory, **không import đồng thời cả hai nếu chúng cùng khai báo bean name `orderStore` mà không có chính sách conflict**.

CheckoutService không phải thay. Đây là lợi ích chính của **configurable composition**.

### 10.1. Tránh import hai implementation cùng lúc

Nếu hai configurations cùng chứa:

```java
@Bean
OrderStore orderStore() { /*...*/ }
```

trong một context, chúng có thể đụng **cùng bean name** `orderStore`. Tùy thiết lập, việc định nghĩa trùng có thể gây lỗi hoặc một definition ghi đè definition khác. Không nên dựa vào thứ tự import để lựa chọn implementation.

Nếu hai methods có **khác tên** nhưng cùng return type `OrderStore`, injection đơn theo type cũng có thể bị mơ hồ: `NoUniqueBeanDefinitionException`.

Cách rõ hơn:

- Composition root chỉ import **một** implementation mặc định trong deployment.
- Nếu cần hai implementation hợp lệ, dùng tên/`@Qualifier`/`@Primary` theo nhu cầu nghiệp vụ.
- Với Spring Boot auto-configuration, các condition như `@ConditionalOnMissingBean` thường giải quyết bean mặc định, nhưng đó là **API của Spring Boot**, không tự có trong project Spring Framework thuần chỉ dùng `spring-context`.

## 11. @Qualifier và @Primary trong module cấu hình

Giả sử hệ thống có hai adapters cùng kiểu:

```java
@Bean("fastStore")
OrderStore fastStore() {
    return new FastOrderStore();
}

@Bean("durableStore")
OrderStore durableStore() {
    return new DurableOrderStore();
}
```

`FastOrderStore` và `DurableOrderStore` là các kiểu giả định, code chỉ minh họa hai bean registrations. Khi Checkout yêu cầu một bean nhất định:

```java
@Bean
CheckoutService checkoutService(
        @Qualifier("durableStore") OrderStore store,
        DiscountPolicy discounts) {
    return new CheckoutService(store, discounts);
}
```

Cần import `Qualifier`. Spring sẽ chọn candidate phù hợp theo qualifier. Nếu có một bean mặc định nên dùng khi injection không gắn qualifier, có thể đánh dấu `@Primary` lên một bean definition phù hợp.

**Quy ước:** qualifier nên diễn đạt vai trò như `durableStore`, `reportingStore`, `paymentClient` hơn là tên implementation chi tiết dễ đổi. Mặc dù tên bean là string, có thể dùng custom qualifier annotation để giảm phụ thuộc literal nếu số điểm injection lớn.

## 12. Profiles và điều kiện chọn biến thể

Trong thực tế, nhiều ứng dụng cần in-memory storage cho local/test và database cho production. Spring cung cấp `@Profile` để giới hạn bean/configuration theo active profiles.

Ví dụ cấu trúc:

```java
@Configuration(proxyBeanMethods = false)
@Profile("local")
class LocalStorageConfiguration {
    @Bean
    OrderStore orderStore() {
        return new InMemoryOrderStore();
    }
}

@Configuration(proxyBeanMethods = false)
@Profile("prod")
class ProductionStorageConfiguration {
    @Bean
    OrderStore orderStore(DataSource dataSource) {
        return new ProductionOrderStore(dataSource);
    }
}
```

`ProductionOrderStore` là kiểu giả định; cần `DataSource` và code persistence thật. Đây không phải phần chạy của demo chính.

Nếu composition root import cả hai nhưng chỉ một profile tương ứng active, Spring có thể chọn bean definitions theo `@Profile`. Tuy nhiên cần xác định rõ behavior khi **không profile nào active**, **cả hai active** hoặc các conditional registrations tương tác. Nếu cả hai profile active và cùng tạo tên `orderStore`, vẫn có nguy cơ conflict.

Đừng xem profile như một cơ chế hoàn chỉnh để quản lý mọi feature toggle. Feature flags runtime có thể thay đổi trong quá trình ứng dụng chạy; `@Profile` chủ yếu ảnh hưởng **đăng ký bean khi context được xây dựng**.

### @Conditional trong Spring Core

Spring hỗ trợ `@Conditional` với implementation `Condition` để điều khiển registration bằng điều kiện cấu hình. Nhưng viết condition phức tạp chỉ để tránh import hai configurations thường làm khó debug. Với composition root tường minh, chọn đúng module từ đầu có thể rõ hơn.

`@ConditionalOnProperty` và `@ConditionalOnMissingBean` là công cụ thường gặp trong **Spring Boot**, không phải những annotation cơ bản của `spring-context` trong demo hiện tại.

## 13. Test từng ranh giới cấu hình

Cấu hình chia module giúp tạo **context nhỏ** cho từng test.

### 13.1. Test riêng PricingConfiguration

```java
try (AnnotationConfigApplicationContext context =
         new AnnotationConfigApplicationContext(
                 PricingConfiguration.class)) {

    DiscountPolicy policy =
            context.getBean(DiscountPolicy.class);

    if (policy.discountedTotal(100) != 90) {
        throw new AssertionError("unexpected discount");
    }
}
```

Đây là đoạn code dựa vào file `PricingConfiguration` và `DiscountPolicy` của demo; cần import cả hai cùng `AnnotationConfigApplicationContext`. Test chỉ cần module Pricing; không cần storage và checkout.

### 13.2. Test Checkout với fake store và policy

Vì `CheckoutService` chỉ phụ thuộc interfaces, có thể kiểm thử thuần Java:

```java
OrderStore fake = new InMemoryOrderStore();
DiscountPolicy fixed = baseTotal -> baseTotal - 5;

CheckoutService service = new CheckoutService(fake, fixed);

String result = service.checkout("TEST-1", 20);
if (!"TEST-1:15".equals(result)) {
    throw new AssertionError(result);
}
```

Snippet này dùng các kiểu trong demo (cần imports). Không cần ApplicationContext. Đây là **unit test nghiệp vụ**, không kiểm chứng khả năng ghép bean definitions.

### 13.3. Test composition root bằng context thật

Dựng context với `ApplicationConfiguration.class` và kiểm tra:

- `OrderStore` có đúng một candidate.
- `DiscountPolicy` có đúng một candidate.
- `CheckoutService` được tạo và có behavior đúng.
- Không có bean ngoài ý muốn từ module chưa import.
- Tài nguyên chung (nếu có) đúng scope và chỉ tạo một lần.

Demo `App.main()` chính là một integration smoke test đơn giản với bảy dòng stdout xác định. Trong dự án có JUnit 5, có thể dùng assertions thay vì `System.out.println`.

### 13.4. Test cấu hình thay thế

Một **test composition root** khác có thể import:

```java
@Configuration(proxyBeanMethods = false)
@Import({
    FakeStorageConfiguration.class,
    PricingConfiguration.class,
    CheckoutConfiguration.class
})
class CheckoutTestConfiguration {}
```

`FakeStorageConfiguration` là một class test giả định trả `OrderStore` fake. Đây là cấu hình riêng cho test, không thêm bean fake vào production root. Cần đảm bảo **chỉ có một candidate hợp lệ** cho injection point, không tạo hai `OrderStore` cùng type rồi dựa vào thứ tự ngẫu nhiên.

### 13.5. Đừng dùng cả application context khi test một utility

Test service logic thuần Java có thể tạo object trực tiếp. Dùng Spring context khi đang kiểm thử **wiring/configuration**, scope, qualifier, bean lifecycle hoặc integration hạ tầng. Cách chia cấu hình hợp lý cho phép chọn test scope nhỏ nhất mà vẫn chứng minh được điều cần kiểm tra.

## 14. Phương pháp giữ module độc lập khi dùng @ComponentScan

Một module có vài chục service có thể muốn quét theo package riêng thay vì khai báo `@Bean` cho từng class. Có thể cho `FeatureConfiguration` thực hiện scan **đúng package feature**:

```java
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = OrdersPackageMarker.class)
class OrdersFeatureConfiguration {}
```

`OrdersPackageMarker` là marker type do ứng dụng đặt trong package orders, và các `@Service`/`@Repository` liên quan nằm trong package đó hoặc package con. Đây là snippet minh họa, cần các imports và marker thực tế.

Hai nguyên tắc quan trọng:

1. **Composition root vẫn có thể dùng `@Import(OrdersFeatureConfiguration.class)`** để chọn feature; scan chỉ hoạt động bên trong feature được nạp.
2. Nếu component scan quá rộng, nó có thể tìm thêm các `@Configuration` của tính năng khác do `@Configuration` là specialization của `@Component`. Khi ấy “chỉ import feature A” vẫn có thể gián tiếp nhận bean feature B nếu package scan không được giới hạn cẩn thận.

Các `includeFilters`/`excludeFilters` và `useDefaultFilters=false` có thể tinh chỉnh scanning, nhưng không phải công cụ chính để sửa một cấu trúc package thiếu rõ ràng.

### Một lưu ý về “internal configuration”

Một configuration class `@Configuration` trong package nội bộ có thể bị Spring tìm thấy nếu một component scan khác bao trùm package ấy. Không nên dựa vào Java package name để khẳng định module không bao giờ được register. Giới hạn scan và dùng import graph tường minh nếu module activation là yêu cầu quan trọng.

## 15. Kết hợp @Import với lớp cấu hình dùng @Bean parameters

Giả sử một feature muốn dùng `Clock`, `OrderStore` và `DiscountPolicy`:

```java
@Configuration(proxyBeanMethods = false)
class CheckoutConfiguration {
    @Bean
    CheckoutService checkoutService(
            OrderStore store,
            DiscountPolicy policy) {
        return new CheckoutService(store, policy);
    }
}
```

Method chỉ khai báo những gì cần; nơi cấp các beans có thể đến từ `StorageConfiguration` và `PricingConfiguration`. Spring cho phép dependency resolution xuyên classes được import **không cần gọi direct `@Bean` methods**.

Không cần viết:

```java
@Configuration
class CheckoutConfiguration {
    @Autowired
    private StorageConfiguration storageConfiguration;

    @Bean
    CheckoutService checkoutService() {
        return new CheckoutService(
                storageConfiguration.orderStore(),
                new TenPercentDiscountPolicy()
        );
    }
}
```

Đây là **anti-pattern minh họa**; nếu `StorageConfiguration` dùng `proxyBeanMethods=false`, direct call có thể tạo store khác ngoài singleton managed. Nếu dùng full interception, code vẫn khiến checkout biết concrete configuration, làm coupling tăng. Injection bằng interface qua method parameter đơn giản hơn.

Tài liệu Spring còn cảnh báo rằng class `@Configuration` được xử lý sớm. Field/constructor injection trên **chính configuration class** có thể dẫn tới khởi tạo sớm không mong muốn, nhất là khi cấu hình chứa `BeanFactoryPostProcessor`/`BeanPostProcessor`. Với processors cần tạo rất sớm, thường dùng `static @Bean` cùng return type thích hợp. Khi khai báo bean nghiệp vụ thông thường, parameter injection là cách dễ kiểm soát dependency nhất.

## 16. Ranh giới module và vòng đời tài nguyên

Một cấu hình chức năng có thể tạo resource đắt tiền:

```java
@Configuration(proxyBeanMethods = false)
class MessagingConfiguration {
    @Bean(destroyMethod = "close")
    VendorMessageClient messageClient(MessagingSettings settings) {
        return VendorMessageClient.create(settings);
    }
}
```

`VendorMessageClient`/`MessagingSettings` là kiểu SDK giả định; code này minh họa ý tưởng ownership/lifecycle. Nếu thư viện có `close()` phù hợp và resource do ứng dụng sở hữu, container sẽ gọi destroy callback theo định nghĩa khi đóng bean/context trong điều kiện phù hợp.

Ranh giới chức năng không chỉ nói bean nằm trong file nào, mà còn **ai sở hữu tài nguyên**:

- Shared HTTP client có thể thuộc `SharedIntegrationConfiguration`.
- Connection pool có thể thuộc `PersistenceConfiguration`.
- Một per-request builder có thể thuộc feature configuration với `prototype` hoặc scope phù hợp.
- Feature service thường chỉ inject hợp đồng tài nguyên; **không tự tạo hoặc tự đóng resource dùng chung**.

Nếu mỗi feature tự `new DataSource()` hoặc tạo pool độc lập, tách cấu hình có thể làm phân tán ownership và tăng rò rỉ tài nguyên. Chia module đúng phải giúp quyền sở hữu rõ hơn, không làm phức tạp hơn.

## 17. Khi nào chia sang Gradle/Maven module riêng?

Tách `@Configuration` theo class/package là ranh giới **logic trong một codebase**. Khi quy mô lớn hơn, bạn có thể tách module vật lý:

```text
checkout-api/
checkout-impl/
pricing-api/
pricing-impl/
storage-api/
storage-jdbc/
application/
```

Nhưng điều này thêm chi phí build, dependency management, public API governance và deploy. Cân nhắc khi:

- Có nhiều nhóm thay đổi độc lập.
- Muốn compiler/build ngăn implementation dependency bị đảo hướng.
- Có test hoặc artifact cần reuse riêng.
- Muốn tách adapter theo environment mà không kéo mọi dependency vào một package lớn.

Ngay cả khi đã có nhiều Gradle/Maven modules, Spring vẫn có thể ghép các configuration classes vào **một ApplicationContext** qua `@Import`, scan phù hợp hoặc auto-configuration. Module build-time và bean container runtime là hai khái niệm khác nhau.

**Tránh phát biểu sai:** mỗi Gradle module không tự động có một Spring context; mỗi `@Configuration` cũng không tự động có singleton cache riêng.

## 18. Các phương án ghép cấu hình khác nhau

### 18.1. @Import trực tiếp

```java
@Configuration(proxyBeanMethods = false)
@Import({
    StorageConfiguration.class,
    PricingConfiguration.class,
    CheckoutConfiguration.class
})
class ApplicationConfiguration {}
```

Ưu điểm: explicit, dễ code review. Phù hợp số modules vừa phải.

### 18.2. @Import ở cấu hình cấp feature

Nếu một feature có nhiều phần nội bộ:

```java
@Configuration(proxyBeanMethods = false)
@Import({
    FeatureDomainConfiguration.class,
    FeatureAdaptersConfiguration.class
})
class FeatureConfiguration {}
```

`FeatureDomainConfiguration`/`FeatureAdaptersConfiguration` là các class giả định. Composition root import `FeatureConfiguration`. Mẫu này hữu ích nếu một feature thật sự sở hữu hai cấu hình con, chứ không phải để mỗi feature âm thầm import cả toàn bộ ứng dụng.

### 18.3. Context.register nhiều cấu hình

```java
try (AnnotationConfigApplicationContext ctx =
         new AnnotationConfigApplicationContext()) {
    ctx.register(
        StorageConfiguration.class,
        PricingConfiguration.class,
        CheckoutConfiguration.class
    );
    ctx.refresh();
    CheckoutService service = ctx.getBean(CheckoutService.class);
}
```

Đây là snippet có thể dùng với các class demo và imports phù hợp. Nó ghép trực tiếp nhiều config từ bootstrap, không cần `@Import`. Phù hợp bootstrap cần chọn module bằng code; tuy nhiên một `ApplicationConfiguration` làm composition root thường giúp tập cấu hình hiển thị rõ ràng và dễ reuse hơn.

### 18.4. Spring Boot auto-configuration

Spring Boot có cơ chế tự động cấu hình bean dựa trên classpath/properties và các conditions. Đây là cấp độ tổ hợp mạnh hơn, phù hợp thư viện/starter reusable, nhưng **không phải bắt buộc** để ghép 3–10 chức năng trong ứng dụng Spring đơn giản. Đừng áp dụng `@ConditionalOnMissingBean` của Boot vào demo thuần `spring-context` mà chưa thêm dependencies tương ứng.

### 18.5. @ImportSelector và ImportBeanDefinitionRegistrar

Đây là các extension point cấp cao giúp quyết định import/đăng ký definitions bằng code và metadata. Chúng có ích khi xây framework, starter hoặc module plugin, nhưng tăng độ khó truy vết bean. Với ghép module tường minh, thường chưa cần.

## 19. Các lỗi thường gặp

### Lỗi 1 — Chỉ tạo class @Configuration nhưng không nạp vào context

Bean definitions không tự xuất hiện chỉ vì file có trong source. Kiểm tra `@Import`, `context.register(...)`, component scan và những cơ chế đăng ký khác. Nếu `ApplicationConfiguration` không được nạp, import graph của nó cũng không được xử lý.

### Lỗi 2 — Mỗi module tự tạo implementation của module khác

Ví dụ Checkout tạo `new InMemoryOrderStore()` thay vì nhận `OrderStore` bean. Hậu quả là hai store khác nhau, mất shared state/lifecycle, test khó thay thế. Dùng interface injection qua constructor/`@Bean` parameters.

### Lỗi 3 — Đặt @ComponentScan ở package gốc quá rộng

Scanner có thể phát hiện cả `@Configuration`, test adapters hoặc implementation dự kiến chỉ dùng cho môi trường khác. Giới hạn scan theo feature hoặc dùng `@Import` explicit.

### Lỗi 4 — Một bean được đăng ký hai lần

Nếu cùng implementation vừa có `@Component` trong package đang scan, vừa có `@Bean` tạo object khác, Spring có thể có nhiều bean cùng type hoặc trùng tên. Rà soát tất cả đường đăng ký và chỉ duy trì registration có chủ ý.

### Lỗi 5 — Hai module cùng khai báo bean name giống nhau

Hai phương thức tên `orderStore` ở các configurations khác nhau có thể xung đột definition. Đừng dựa vào việc BeanDefinition overriding luôn được phép. Thiết kế mỗi tên bean có chủ sở hữu rõ.

### Lỗi 6 — Có hai beans cùng interface mà Checkout không biết chọn

`CheckoutConfiguration.checkoutService(OrderStore store, ...)` có thể gây `NoUniqueBeanDefinitionException`. Dùng `@Qualifier`/`@Primary` khi nhiều candidate là chủ ý hoặc chỉ import một implementation.

### Lỗi 7 — Dùng @Profile mà quên profile mặc định

Khi cả hai implementations đều chỉ tồn tại theo `local`/`prod`, không active profile phù hợp sẽ thiếu bean. Nếu cả hai active, có thể trùng tên hoặc mơ hồ. Kiểm thử rõ từng environment.

### Lỗi 8 — Lạm dụng @Import theo dạng vòng

Nếu storage import checkout và checkout lại import storage, kiến trúc trở nên khó đoán và có thể tạo bean circular dependencies. Giữ composition root làm nơi chọn modules; code nghiệp vụ chỉ phụ thuộc hợp đồng.

### Lỗi 9 — Dựa vào thứ tự khai báo trong @Import

Các dependency nên được Spring giải quyết qua parameter injection. Đừng giả định modules được instantiate theo thứ tự trong mảng `@Import` như một quy tắc nghiệp vụ; Spring có dependency graph, scope, lazy beans và infrastructure lifecycle riêng.

### Lỗi 10 — Tạo tài nguyên nặng ở nhiều configuration

Hai modules mỗi nơi `@Bean ObjectMapper`/client/pool khác nhau có thể hợp lý khi policy khác nhau, nhưng nguy hiểm nếu vô tình nhân đôi connection pool. Xác định scope và ownership, reuse bean chung qua injection khi thực sự cần.

### Lỗi 11 — Ghép tất cả configuration vào mọi test

Context quá lớn gây test chậm, yêu cầu database/network không cần thiết và khó tách nguyên nhân lỗi. Tạo fixture nhỏ chỉ import module đang test, cộng các fake dependencies cần thiết.

### Lỗi 12 — Dùng @Configuration(proxyBeanMethods=false) nhưng gọi chéo @Bean

Lời gọi trong thân factory method là Java direct call, có thể tạo object mới ngoài managed bean. Dùng parameter injection thay thế. Dù full mode có thể intercept inter-bean calls, explicit parameters vẫn dễ hiểu và ít coupling hơn.

### Lỗi 13 — Nhầm import graph với runtime visibility constraint

`@Import` chỉ nạp định nghĩa bean; nó không cấm class khác `getBean(...)` hoặc inject một type mà thiết kế kiến trúc muốn giữ private. Cần module boundaries/code review/test kiến trúc để kiểm soát hướng phụ thuộc.

### Lỗi 14 — Nghĩ @Profile thay thế composition root

`@Profile` chọn registrations theo môi trường, nhưng không giúp giải thích **module nào sở hữu bean** hoặc ngăn profile bị bật đồng thời. Dùng profile cho biến thể có chủ đích; composition root vẫn nên thể hiện tập feature.

## 20. Checklist khi thiết kế cấu hình chức năng

Trước khi tạo một configuration class mới, trả lời:

1. **Chức năng nào chịu trách nhiệm?** Storage, pricing, checkout, billing, reporting hay shared infra?
2. **Public contract là gì?** Interface/service nào module khác được phép tiêu thụ?
3. **Ai đăng ký bean implementation?** Nên có một nơi sở hữu bean definition và lifecycle.
4. **Cấu hình được nạp bằng cách nào?** `@Import`, direct register, component scan hay Boot auto-configuration?
5. **Module nào phụ thuộc module nào?** Hướng dependency có nhất quán và không tạo vòng không?
6. **Có nhiều implementation cùng type không?** Nếu có, qualifier/primary/conditional selection được thiết kế rõ chưa?
7. **Có cần scan tự động không?** Nếu không, explicit `@Bean` thường dễ theo dõi hơn.
8. **Có tài nguyên dùng chung không?** Những bean nào nên singleton, prototype, lazy, có destroy callback hay external ownership?
9. **Test tối thiểu cần module nào?** Có thể dựng context cho riêng feature hay unit test bằng object thường không?
10. **Có thể thay adapter mà không sửa service không?** Nếu không, cân nhắc tách interface và composition logic.

## 21. Câu hỏi tự luyện và đáp án

**Câu 1.** Một context nạp `ApplicationConfiguration` với `@Import(StorageConfiguration.class, PricingConfiguration.class)`. Có cần `@ComponentScan` để nhận `@Bean` methods trong hai configuration này không?

**Đáp:** Không. `@Import` nạp các configuration và Spring xử lý các `@Bean` methods của chúng. Component scanning là một đường tìm class khác.

**Câu 2.** CheckoutConfiguration có method `checkoutService(OrderStore store, DiscountPolicy policy)`. Bean `OrderStore` đến từ StorageConfiguration. Spring nối hai class cấu hình bằng cách nào?

**Đáp:** Khi tạo `checkoutService`, container resolve hai tham số dựa trên bean definitions/type/qualifier đang có trong cùng context. CheckoutConfiguration không cần gọi trực tiếp StorageConfiguration.

**Câu 3.** Mỗi `@Configuration` có tạo một context riêng không?

**Đáp:** Không. Trong ví dụ, một `AnnotationConfigApplicationContext` chứa definitions được import từ ba configuration classes.

**Câu 4.** Vì sao không nên để PricingConfiguration import CheckoutConfiguration chỉ vì Checkout dùng DiscountPolicy?

**Đáp:** Đó là đảo hướng composition/dependency. Pricing không cần biết use case dùng nó; composition root chọn cả hai, còn Checkout khai báo tham số `DiscountPolicy`.

**Câu 5.** Có thể tách implementation JDBC khỏi in-memory mà không sửa CheckoutService không?

**Đáp:** Có. CheckoutService dùng interface `OrderStore`. Composition root chọn một configuration cung cấp implementation tương ứng; chú ý không để hai beans không phân định cùng xuất hiện.

**Câu 6.** Vì sao `@Import` phù hợp khi cần nhìn rõ các modules được bật?

**Đáp:** Mảng import là danh sách tường minh các nguồn cấu hình. Component scan có thể chọn thêm classes tùy package/filter/classpath và cần kiểm tra phạm vi để biết tập bean thật.

**Câu 7.** Nếu hai beans cùng implement `OrderStore`, tiêm `OrderStore` vào Checkout có luôn được không?

**Đáp:** Không chắc. Khi nhiều candidate không xác định được ứng viên duy nhất, Spring báo `NoUniqueBeanDefinitionException`. Cần qualifier, primary hoặc chỉ một implementation cho deployment.

**Câu 8.** Trong demo, `writes=2` chứng minh điều gì?

**Đáp:** Hai lần checkout sử dụng cùng managed singleton `OrderStore` mà entry point đọc lại từ context. Module composition giữ dependency identity nhất quán.

**Câu 9.** Nếu dùng `@ComponentScan(basePackageClasses=Marker.class)` thì Spring chỉ quét một class Marker đúng không?

**Đáp:** Không. Nó dùng package chứa Marker làm gốc để scan, kể cả các package con tương ứng.

**Câu 10.** Một bean được loại khỏi một component scan có chắc không xuất hiện trong context không?

**Đáp:** Không. Bean có thể được đăng ký bằng `@Bean`, `@Import` hoặc scan từ nơi khác. Phải kiểm tra tất cả đường đăng ký.

**Câu 11.** Có nên cho shared HTTP pool vào cả PricingConfiguration và CheckoutConfiguration?

**Đáp:** Chỉ nếu thật sự cần hai pools với policy/lifecycle độc lập. Nếu là tài nguyên chia sẻ, nên có một owner configuration, rồi các feature inject cùng bean được quản lý.

**Câu 12.** Khi test DiscountPolicy, có cần dựng luôn Storage và Checkout modules không?

**Đáp:** Không. Với unit test, tạo policy trực tiếp; với wiring test, chỉ import PricingConfiguration. Context tối thiểu giúp test nhanh và rõ nguyên nhân.

## 22. Tài liệu chính thức để đối chiếu

- [Spring Framework 6.2 — Composing Java-based Configurations](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/composing-configuration-classes.html): `@Import`, ghép cấu hình, injection xuyên configuration classes và lưu ý early initialization.
- [Spring Framework 6.2 — Instantiating the Container with AnnotationConfigApplicationContext](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/instantiating-container.html): `register()`, `refresh()` và `scan()`.
- [Spring Framework 6.2 — Basic Concepts: @Bean and @Configuration](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/basic-concepts.html): hai annotation cấu hình cốt lõi.
- [Spring Framework 6.2 — Using the @Bean Annotation](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/bean-annotation.html): factory method parameters, return type, scope và lifecycle.
- [Spring Framework 6.2 — Using the @Configuration Annotation](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/configuration-annotation.html): phương thức cấu hình và inter-bean references.
- [Spring Framework 6.2 — Classpath Scanning and Managed Components](https://docs.spring.io/spring-framework/reference/6.2/core/beans/classpath-scanning.html): quan hệ giữa `@ComponentScan`, stereotype và metadata registration.
- [Spring Framework — Bean Scopes](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html): vòng đời và scope khi ghép nhiều bean definitions.

**Ghi nhớ:** Chia cấu hình theo ranh giới chức năng giúp module có trách nhiệm và ownership rõ ràng. `@Import` ghép những nguồn cấu hình cần thiết vào **một Spring IoC Container**, còn **tham số `@Bean`**/constructor injection nối các bean thông qua hợp đồng thay vì thông qua lời gọi trực tiếp giữa configuration classes. Composition root quyết định *cái gì được lắp vào ứng dụng*; mỗi module quyết định *mình cung cấp bean nào*; Spring giải quyết *cách các dependency gặp nhau*.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="configuration-composition-002">Nhập lớp cấu hình bằng @Import</a>

<details>
<summary>Xem chi tiết</summary>

# Nhập lớp cấu hình bằng `@Import` trong Spring Core Container

## 1. Vấn đề: một ứng dụng cần nhiều Java configuration classes

Một ứng dụng Spring nhỏ có thể đặt tất cả factory methods trong một class `@Configuration`. Khi hệ thống lớn dần, code thường được tách theo trách nhiệm: repository, nghiệp vụ, hạ tầng, tính năng tùy chọn. Khi khởi tạo `ApplicationContext`, làm thế nào ghép các cấu hình mà không phải đăng ký thủ công từng class ở mọi điểm khởi động?

Spring hỗ trợ annotation `org.springframework.context.annotation.Import`. Ví dụ:

```java
@Configuration(proxyBeanMethods = false)
class RepositoryConfig {
    @Bean
    CustomerRepository customerRepository() {
        return new InMemoryCustomerRepository();
    }
}

@Configuration(proxyBeanMethods = false)
@Import(RepositoryConfig.class)
class ServiceConfig {
    @Bean
    CustomerService customerService(CustomerRepository repository) {
        return new CustomerService(repository);
    }
}
```

Đây là **đoạn mã minh họa**, chưa phải file Java chạy độc lập: `CustomerRepository`, `InMemoryCustomerRepository` và `CustomerService` là các kiểu ứng dụng cần định nghĩa. Khi đăng ký `ServiceConfig` vào Spring container, `@Import(RepositoryConfig.class)` khiến Spring **xử lý cả `RepositoryConfig`**, bao gồm bean definition của `customerRepository()`. Tham số factory method `CustomerRepository repository` được container giải quyết theo quy tắc dependency injection.

Điểm cốt lõi: `@Import` **ghép các nguồn cấu hình một cách tường minh**. Nó không có nghĩa “gọi constructor của một class ngay lập tức”, cũng không phải “quét tất cả class trong package được nhập”.

Bài học này tập trung vào:

1. Cú pháp `@Import` với một hoặc nhiều configuration classes và cách tạo entry-point duy nhất.
2. Bean definition, quan hệ phụ thuộc và việc import bắc cầu.
3. Import các **regular component classes**, không chỉ `@Configuration`.
4. Khác biệt giữa `@Import`, `@ComponentScan`, `@Bean`, `@ImportResource` và `@Enable...`.
5. Mở rộng import với `ImportSelector`, `DeferredImportSelector` và `ImportBeanDefinitionRegistrar`.
6. Cách dùng trong ứng dụng phân mô-đun, tính năng có điều kiện, kiểm thử và xử lý lỗi.
7. Dự án Java 17+ chạy được trên Spring Framework 6.2, có cấu trúc Gradle và stdout dự kiến.

## 2. `@Import` là gì?

`@Import` là annotation được khai báo ở **type level**. Nó nhận một mảng `Class<?>`, có thể viết một class hoặc nhiều class:

```java
@Import(DatabaseConfig.class)
```

```java
@Import({DatabaseConfig.class, ServiceConfig.class, MetricsConfig.class})
```

Khi cấu hình mang `@Import` được Spring xử lý, các import candidates được bổ sung vào quá trình xử lý configuration classes và đăng ký bean definitions. Trong Spring Framework 6.2, `@Import` hỗ trợ bốn nhóm phổ biến:

| Đối tượng đưa vào `@Import` | Điều Spring làm | Mục đích |
| --- | --- | --- |
| Class `@Configuration` | Xử lý class cấu hình, đăng ký các `@Bean` methods và các metadata liên quan | Ghép các cấu hình khai báo tĩnh |
| Regular component class | Đăng ký class như một component/bean definition phù hợp mà không cần component scanning | Chọn rõ những class ứng dụng muốn đưa vào container |
| Implementation của `ImportSelector` | Gọi selector để xác định các class cần import dựa trên metadata/cấu hình | Chọn cấu hình theo điều kiện hoặc annotation attributes |
| Implementation của `ImportBeanDefinitionRegistrar` | Cho phép đăng ký bean definitions bằng code trên `BeanDefinitionRegistry` | Extension point hạ tầng cần quyền kiểm soát mức registry |

Kể từ Spring Framework 4.2, `@Import` có thể nhập **regular component classes**, tương tự ý tưởng đăng ký một class qua `AnnotationConfigApplicationContext.register(...)`. Vì vậy, đối tượng import không bắt buộc luôn là `@Configuration`.

**Điều không thuộc chức năng trực tiếp của `@Import`:** nhập tài nguyên XML dưới dạng bean definitions. Với XML hoặc những resource bean definitions khác được hỗ trợ, cơ chế liên quan là `@ImportResource`. Cũng không thể truyền một chuỗi package vào `@Import`: đây là annotation yêu cầu các `Class<?>` cụ thể.

### 2.1. `@Import` là ghép cấu hình, không phải Java `import` statement

Hai cú pháp cùng có từ “import” nhưng vai trò khác nhau:

```java
import com.example.storage.StorageConfig;  // Java compiler: dùng tên class.

@Import(StorageConfig.class)               // Spring: đưa config vào container.
```

Lệnh Java `import` ở đầu source chỉ giúp trình biên dịch giải quyết tên kiểu; nó **không đăng ký bean**. Spring `@Import` thực sự tham gia vào giai đoạn phân tích bean definitions của container.

### 2.2. Khi nào quá trình import diễn ra?

Một luồng tổng quát:

```text
new AnnotationConfigApplicationContext(AppConfig.class)
                         |
                         v
           Đăng ký AppConfig làm đầu vào
                         |
                         v
        Xử lý @Configuration và @Import
                         |
          +--------------+--------------+
          |                             |
          v                             v
     @Bean của AppConfig          Import classes
                                        |
                     +------------------+--------------------+
                     |                  |                    |
                     v                  v                    v
                @Configuration     ImportSelector    Regular component
                     |                  |                    |
                     v                  v                    v
                  @Bean defs      Config candidates      Bean definition
                     |
                     +------------------+--------------------+
                                        |
                                        v
                           BeanFactory/registry
                                        |
                                        v
                        DI, instantiation, lifecycle
```

Spring giải quyết việc import và tạo **bean definitions** trong lúc xử lý cấu hình. Sau đó bean instances mới được tạo theo scope/lazy rules và dependency graph. Không nên kết luận rằng thứ tự các class trong mảng `@Import` là thứ tự khởi tạo bean, hoặc rằng mọi constructor của class import đã được gọi ngay khi đọc annotation.

## 3. Ghép hai configuration classes và khai báo dependency xuyên cấu hình

Xét hai lớp cấu hình:

```java
@Configuration(proxyBeanMethods = false)
class PersistenceConfig {

    @Bean
    InventoryRepository inventoryRepository() {
        return new InMemoryInventoryRepository();
    }
}

@Configuration(proxyBeanMethods = false)
@Import(PersistenceConfig.class)
class ApplicationConfig {

    @Bean
    InventoryService inventoryService(InventoryRepository repository) {
        return new InventoryService(repository);
    }
}
```

Các kiểu domain trong trích đoạn được định nghĩa ở ứng dụng thực tế. Chỉ cần đưa `ApplicationConfig` vào `AnnotationConfigApplicationContext`; `PersistenceConfig` sẽ được xử lý nhờ `@Import`.

**Quan hệ dependency được Spring giải quyết qua factory-method parameter**, không qua lời gọi Java `inventoryRepository()` ở config khác. `InventoryService` có thể dùng một singleton repository được Spring quản lý; không cần injection chính `PersistenceConfig` vào `ApplicationConfig`.

Phương pháp này hữu ích với `@Configuration(proxyBeanMethods = false)`. Ở lite mode, một lời gọi Java trực tiếp tới một `@Bean` method không có cơ chế CGLIB inter-bean interception; còn dependency parameter vẫn được BeanFactory resolve đúng theo container semantics.

### 3.1. Mỗi bean có tên và scope riêng

`@Import` không biến tất cả factory methods của các class khác nhau thành “một bean”. Mỗi `@Bean` method được xử lý thành một bean definition riêng với bean name/qualifier/scope tùy cấu hình. Tên mặc định của bean do `@Bean` method tạo thường chính là **tên method**, ví dụ `inventoryRepository`.

Khi context tồn tại:

```java
InventoryRepository repository = context.getBean(InventoryRepository.class);
InventoryService service = context.getBean(InventoryService.class);
```

Đây là trích đoạn minh họa khi `context` đã được tạo. Với singleton scope mặc định, Spring thường trả cùng instance mỗi lần resolve theo bean name/type trong cùng BeanFactory.

### 3.2. Import bắc cầu

Class đã import có thể tiếp tục mang `@Import`:

```java
@Configuration
@Import(PersistenceConfig.class)
class ServiceConfig {
}

@Configuration
@Import(ServiceConfig.class)
class MainConfig {
}
```

Đây là mã minh họa. Đăng ký `MainConfig` khiến Spring xử lý cấu trúc import đi qua `ServiceConfig` tới `PersistenceConfig`. Điều đó giúp tổ chức các **tập cấu hình có chủ đích**, thay vì bắt application main biết từng config chi tiết.

Không nên tạo vòng import A → B → A. Spring configuration processor có cơ chế phát hiện vòng nhập không hợp lệ và có thể báo lỗi; sự phức tạp khi import vòng cũng làm ownership của module mơ hồ. Nếu hai module cần nhau, ưu tiên trích phần chung hoặc điều chỉnh chiều phụ thuộc.

### 3.3. Nhập một configuration class nhiều lần

Một config có thể được tham chiếu từ nhiều điểm của đồ thị import. Spring configuration processing có cơ chế xử lý candidates trùng nhằm tránh đăng ký lại cùng configuration một cách vô nghĩa trong các trường hợp bình thường. **Không dùng import lặp để mong tạo hai singleton instances.** Nếu muốn hai bean cấu hình khác nhau từ cùng implementation, nên khai báo hai bean definitions khác tên/parameters có chủ đích.

Cũng cần tránh trùng tên `@Bean` giữa các configuration classes không tương thích. Dù hai class khác nhau, cùng method `@Bean service()` mặc định cùng bean name `service`; chính sách bean overriding có thể khiến definition bị thay thế hoặc gây lỗi. Hãy đặt tên rõ khi trách nhiệm khác nhau và kiểm thử registry.

## 4. `@Import` và `@ComponentScan`: lựa chọn theo ranh giới

Hai annotation đều có thể dẫn đến việc Spring có thêm bean definitions, nhưng chúng giải quyết hai bài toán khác nhau.

| Tiêu chí | `@Import` | `@ComponentScan` |
| --- | --- | --- |
| Khai báo đầu vào | Các class cụ thể, selectors hoặc registrars | Một/một số base packages cùng filters |
| Cần biết từng class? | Với import tĩnh, có | Không cần liệt kê từng component |
| Tự mở toàn bộ package? | Không | Có, xét cả subpackages |
| Hợp với thư viện/điểm tích hợp | Có, configuration entry point công khai | Có nếu ứng dụng chủ ý quản lý toàn bộ package |
| Tác động khi thêm class `@Component` mới trong package | Không tự có bean mới trừ khi được đăng ký qua nguồn khác | Class mới có thể được quét và đăng ký |
| Điều kiện/selector động | `ImportSelector` / conditional metadata | Filters/conditions và những cơ chế khác |
| Có thể kết hợp | Có | Có |

Ví dụ chọn **cụ thể** một component:

```java
@Configuration
@Import(EmailGateway.class)
class MessagingConfig {
}
```

Nếu `EmailGateway` là regular class có constructor phù hợp, Spring có thể đăng ký nó nhờ `@Import` mà không quét cả package chứa gateway. `EmailGateway` không nhất thiết phải được đánh dấu `@Component`; xem ví dụ chạy ở mục 9.

Với quét package:

```java
@Configuration
@ComponentScan(basePackages = "com.example.messaging")
class MessagingConfig {
}
```

Scanner sẽ xem các component candidates trong `com.example.messaging` và subpackages, không chỉ một `EmailGateway`.

### 4.1. Trong ứng dụng nhiều mô-đun

Một cấu trúc hợp lý:

```text
app/
  AppConfig.java               -> @Import({StorageModuleConfig, OrderModuleConfig})
storage/
  StorageModuleConfig.java     -> công bố các beans của storage
  internal/...
orders/
  OrderModuleConfig.java       -> công bố các beans của orders
  internal/...
```

`@Import` hỗ trợ kiến trúc trong đó **mỗi module cung cấp một configuration entry point** và application chọn module nào tham gia container. Điều này tránh quét vô tình toàn bộ package của library chỉ vì JAR nằm trên classpath.

Tuy nhiên, import một configuration class có `@ComponentScan` bên trong **vẫn có thể kích hoạt chính lượt scan đó**. Vì thế `@Import` không tự giới hạn những bean mà config được import đăng ký. Cần đọc cấu hình được nhập để biết phạm vi thực tế.

### 4.2. Component scan không thay thế import module rõ ràng

Trong Spring Boot, đặt `@SpringBootApplication` ở root package có thể quét các component bên dưới. Nhưng không phải thư viện nào cũng muốn người dùng scan package nội bộ. Một library có thể dùng cấu hình do ứng dụng `@Import`, hoặc cung cấp Spring Boot auto-configuration theo quy ước riêng.

**Không đồng nhất component scanning với Spring Data repository scanning hoặc JPA entity scanning.** Các hệ thống này có extension points và vùng scan khác nhau. `@Import` một configuration class có thể kích hoạt `@EnableJpaRepositories`, nhưng bản thân `@Import` không tự biến mọi interface `Repository` trong project thành repository proxy.

## 5. Import regular component class

Javadoc `@Import` của Spring Framework 6.2 cho phép nhập regular component class, không nhất thiết là `@Configuration`. Điều này tạo lối đăng ký tường minh:

```java
class HealthProbe {
    String status() {
        return "ok";
    }
}

@Configuration
@Import(HealthProbe.class)
class AppConfig {
}
```

Đây là đoạn minh họa. Khi đăng ký `AppConfig`, Spring có thể tạo bean `HealthProbe` dù không có `@ComponentScan` và `HealthProbe` không mang stereotype. Miễn class có thể khởi tạo và các dependency hợp lệ, caller có thể:

```java
HealthProbe probe = context.getBean(HealthProbe.class);
```

Tên bean của class được import theo loại registration và `BeanNameGenerator` của configuration processor; đừng mặc định tên chính luôn là `healthProbe` giống default component scanner. Nếu không có nhu cầu tên tường minh, **lookup theo type** thường ít ràng buộc hơn.

### 5.1. Một interface không tự trở thành implementation

`@Import(MyPort.class)` với `MyPort` là interface thuần không tạo ra một implementation hữu dụng. Phải nhập concrete implementation hoặc một configuration/factory/registrar tạo object phù hợp. Một abstract class cũng không thể được container khởi tạo thông thường chỉ vì nó xuất hiện trong `@Import`.

### 5.2. Import class có constructor dependency

Spring có thể phân giải constructor injection cho regular component class được nhập, theo khả năng bean creation của container. Ví dụ:

```java
public class AuditReporter {
    private final AuditStorage storage;

    public AuditReporter(AuditStorage storage) {
        this.storage = storage;
    }
}
```

Nếu `@Import(AuditReporter.class)` được dùng và có một bean `AuditStorage` phù hợp, container có thể inject tham số. Nếu thiếu dependency, bạn vẫn nhận lỗi tạo bean; `@Import` không tự cung cấp các type chưa được đăng ký.

### 5.3. Import không có nghĩa bao gồm toàn bộ package

Giả sử `EmailGateway` và `SmsGateway` nằm cùng package. `@Import(EmailGateway.class)` không tự đăng ký `SmsGateway`. Nếu một `@Configuration` được import lại bật `@ComponentScan` trên package đó, lúc ấy việc phát hiện thêm bean là kết quả của **component scan trong configuration**, không phải vì `@Import` tự quét package.

## 6. `ImportSelector`: chọn configuration động

Với một class selector implement `org.springframework.context.annotation.ImportSelector`, Spring gọi:

```java
String[] selectImports(AnnotationMetadata importingClassMetadata);
```

Selector nhận metadata của class khai báo `@Import` và trả **mảng tên class đầy đủ** sẽ được nhập. Nó giúp tạo annotation bật tính năng và quyết định cấu hình theo attributes/environment hoặc quy tắc hạ tầng.

Ví dụ:

```java
public class StorageSelector implements ImportSelector {
    @Override
    public String[] selectImports(AnnotationMetadata metadata) {
        return new String[] { InMemoryStorageConfig.class.getName() };
    }
}
```

Đây là snippet minh họa, cần import `ImportSelector`, `AnnotationMetadata` và tồn tại `InMemoryStorageConfig`. Khi dùng:

```java
@Configuration
@Import(StorageSelector.class)
class AppConfig {
}
```

Spring xử lý selector và nhập configuration được selector trả về. Trong chương trình ở mục 9, `PromotionSelector` sẽ nhập `PromotionConfig` tương tự.

### 6.1. Metadata cho phép xây annotation cấu hình cấp cao

Một annotation tự định nghĩa có thể dùng `@Import` ở dạng **meta-annotation**:

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(StorageSelector.class)
public @interface EnableStorage {
}
```

Đây là đoạn minh họa: cần imports `java.lang.annotation.*` cùng `@Import`. Ở chỗ khác:

```java
@Configuration
@EnableStorage
class AppConfig {
}
```

Sau đó selector có thể đọc thông tin của importing configuration/annotation để quyết định class cần import. Đây là nền tảng để viết những annotation `@Enable...` có hành vi được quản lý qua configuration import.

Cần phân biệt **bật một module bằng annotation** với **component scanning**; annotation `@EnableStorage` ở ví dụ chỉ nhập selector/config do nó khai báo, không tự quét package ngoài những bước được cấu hình tiếp.

### 6.2. `DeferredImportSelector`

`DeferredImportSelector` kế thừa `ImportSelector` và cho phép trì hoãn thời điểm lựa chọn imports đến giai đoạn sau khi các configuration classes thông thường được xử lý. Đây là extension point nâng cao hữu ích cho các tình huống chọn cấu hình dựa trên bối cảnh rộng hơn, nổi bật trong cách tổ chức auto-configuration.

Không cần `DeferredImportSelector` chỉ để ghép hai configuration classes cố định. `@Import({AConfig.class, BConfig.class})` trực tiếp dễ hiểu hơn và tránh phức tạp trong vòng đời xử lý cấu hình.

### 6.3. Selector không phải bean nghiệp vụ thông thường

`ImportSelector` là hạ tầng configuration processing. Đừng giả định nó được đăng ký dưới một bean name thông thường để injection vào service giống một `@Component`. Mục đích của nó là **chọn class nào cần import**, không phải tạo object nghiệp vụ bằng các method trên selector.

## 7. `ImportBeanDefinitionRegistrar`: đăng ký bean definitions bằng code

Nếu muốn thao tác ở mức thấp hơn `@Bean`, Spring cho phép class implement `org.springframework.context.annotation.ImportBeanDefinitionRegistrar`. Một method liên quan:

```java
void registerBeanDefinitions(
        AnnotationMetadata importingClassMetadata,
        BeanDefinitionRegistry registry
);
```

Một implementation có thể phân tích annotation metadata, tạo `BeanDefinition` và đăng ký nó vào registry. Ví dụ:

```java
public class DiagnosticsRegistrar implements ImportBeanDefinitionRegistrar {
    @Override
    public void registerBeanDefinitions(
            AnnotationMetadata importingClassMetadata,
            BeanDefinitionRegistry registry) {

        RootBeanDefinition definition =
                new RootBeanDefinition(DiagnosticsClient.class);
        registry.registerBeanDefinition("diagnosticsClient", definition);
    }
}
```

Đây là snippet minh họa, cần các imports:

```java
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;
```

Và cần class `DiagnosticsClient` có constructor/metadata hợp lệ. Trong ứng dụng:

```java
@Configuration
@Import(DiagnosticsRegistrar.class)
class AppConfig {
}
```

Spring có thể đăng ký bean definition `diagnosticsClient`. Registrar cho phép khai báo bean dựa trên metadata mà không cần tạo mỗi factory method cố định ở source.

**Giới hạn lifecycle quan trọng:** theo Javadoc Spring 6.2, không đăng ký `BeanDefinitionRegistryPostProcessor` types qua `ImportBeanDefinitionRegistrar` theo cách tùy ý vì có ràng buộc vòng đời của configuration class processing. Khi cần infrastructure post-processor, chọn cơ chế đăng ký hạ tầng được framework khuyến nghị.

### Khi nào nên dùng registrar?

- Framework/library xây abstraction cấu hình bằng annotation riêng.
- Cần đăng ký số lượng bean definitions phụ thuộc metadata.
- Muốn tự quyết định bean names, implementation types, constructor/factory metadata hoặc các điều kiện ở mức registry.

**Không ưu tiên registrar cho hai object cụ thể.** Nếu chỉ cần `Repository` và `Service`, `@Configuration` với `@Bean` methods rõ ràng thường dễ đọc, dễ debug và dễ kiểm thử hơn.

### 7.1. Sự khác biệt với selector

| Cơ chế | Kết quả chính |
| --- | --- |
| `@Import(SomeConfig.class)` | Nhập trực tiếp class cấu hình |
| `ImportSelector` | Trả class names để Spring tiếp tục xử lý import |
| `DeferredImportSelector` | Chọn import ở giai đoạn trì hoãn |
| `ImportBeanDefinitionRegistrar` | Đăng ký trực tiếp bean definitions trong registry |

Selector trả **tên class cần import**; registrar làm việc với **định nghĩa bean**. Tránh viết một selector mà cố trả bean name thay cho class name; Spring cần class names hợp lệ để xử lý.

## 8. Ví dụ chạy được: Java 17+, Spring 6.2 và một entry-point `@Import`

Dự án hoàn chỉnh dưới đây chỉ đăng ký **một** configuration class vào `AnnotationConfigApplicationContext`. Spring sẽ xử lý:

- `@Import` đưa vào hai configuration classes: `RepositoryConfig` và `BusinessConfig`.
- `@Import` đưa vào một regular class `HealthProbe` dù class đó không có `@Component`.
- `PromotionSelector` thực thi `ImportSelector` và chọn `PromotionConfig`.
- `BusinessConfig` tạo `CheckoutService` từ các bean do những cấu hình khác cung cấp.
- Một class `UnscannedHelper` có `@Component` nhưng **không được import và không nằm trong scan nào**, nên không xuất hiện trong context.

Ví dụ không cần Spring Boot, web server, XML hoặc database.

### 8.1. Cấu trúc file

```text
spring-import-demo/
├── settings.gradle
├── build.gradle
└── src/
    └── main/
        └── java/
            └── ImportCompositionDemo.java
```

File `settings.gradle`:

```groovy
rootProject.name = 'spring-import-demo'
```

File `build.gradle`:

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
    mainClass = 'ImportCompositionDemo'
}
```

Dùng JDK 17 trở lên và phiên bản Gradle tương thích với JDK. `spring-context` kéo theo những module Spring Beans/Core cần thiết; không cần thêm `spring-boot` để dùng `@Import`.

### 8.2. Mã Java đầy đủ

Lưu vào `src/main/java/ImportCompositionDemo.java`:

```java
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.stereotype.Component;

public class ImportCompositionDemo {

    public record CatalogRepository(String source) {
        String find() {
            return source;
        }
    }

    public record DiscountPolicy(int percent) {
    }

    public record CheckoutService(
            CatalogRepository repository,
            DiscountPolicy discountPolicy) {

        int total(int price) {
            return price - price * discountPolicy.percent() / 100;
        }

        String summary() {
            return repository.find() + ":" +
                    discountPolicy.percent() + "%";
        }
    }

    public record AppTag(String text) {
    }

    // Regular class, không có @Component.
    public static class HealthProbe {
        public HealthProbe() {
        }

        String status() {
            return "ok";
        }
    }

    // Có @Component, nhưng không được scan hoặc import.
    @Component
    public static class UnscannedHelper {
    }

    @Configuration(proxyBeanMethods = false)
    public static class RepositoryConfig {
        @Bean
        CatalogRepository catalogRepository() {
            return new CatalogRepository("catalog");
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class PromotionConfig {
        @Bean
        DiscountPolicy discountPolicy() {
            return new DiscountPolicy(10);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class BusinessConfig {
        @Bean
        CheckoutService checkoutService(
                CatalogRepository repository,
                DiscountPolicy discountPolicy) {
            return new CheckoutService(repository, discountPolicy);
        }
    }

    public static class PromotionSelector implements ImportSelector {
        @Override
        public String[] selectImports(
                AnnotationMetadata importingClassMetadata) {
            return new String[] { PromotionConfig.class.getName() };
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
            RepositoryConfig.class,
            BusinessConfig.class,
            HealthProbe.class,
            PromotionSelector.class
    })
    public static class AppConfig {
        @Bean
        AppTag appTag() {
            return new AppTag("imported-composition");
        }
    }

    public static void main(String[] args) {
        try (var context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {
            AppTag tag = context.getBean(AppTag.class);
            CatalogRepository repository =
                    context.getBean(CatalogRepository.class);
            DiscountPolicy policy =
                    context.getBean(DiscountPolicy.class);
            CheckoutService checkout =
                    context.getBean(CheckoutService.class);
            HealthProbe probe = context.getBean(HealthProbe.class);

            System.out.println("tag = " + tag.text());
            System.out.println("repository = " + repository.find());
            System.out.println("discount = " + policy.percent() + "%");
            System.out.println("total200 = " + checkout.total(200));
            System.out.println("summary = " + checkout.summary());
            System.out.println("probe = " + probe.status());
            System.out.println("unscannedCandidates = " +
                    context.getBeanNamesForType(UnscannedHelper.class).length);
            System.out.println("sameRepository = " +
                    (repository == checkout.repository()));
        }
    }
}
```

Đây là chương trình Java đầy đủ: chỉ có một top-level public class `ImportCompositionDemo`; các kiểu domain dùng Java record, còn các cấu hình/selector/probe là static nested classes. `AppConfig` đóng vai trò composition root.

### 8.3. Lệnh chạy và stdout

Trong thư mục `spring-import-demo`:

```bash
gradle run --quiet
```

Nếu sử dụng Gradle Wrapper:

```bash
./gradlew run --quiet
```

Trên Windows PowerShell:

```powershell
.\gradlew.bat run --quiet
```

Các dòng do `System.out.println` trong chương trình tạo ra:

```text
tag = imported-composition
repository = catalog
discount = 10%
total200 = 180
summary = catalog:10%
probe = ok
unscannedCandidates = 0
sameRepository = true
```

Lưu ý: ví dụ xác định những dòng **chương trình** in ra; Gradle hay hệ thống logging có thể in thêm thông báo không thuộc `System.out.println`.

### 8.4. Giải thích từng kết quả

**`tag = imported-composition`**: `AppConfig` được đăng ký trực tiếp. Method `@Bean appTag()` của nó tạo bean `AppTag`.

**`repository = catalog`**: `RepositoryConfig` được import tĩnh, nên bean `catalogRepository` tồn tại; không cần `@ComponentScan`.

**`discount = 10%`**: `PromotionSelector` được import và trả tên class `PromotionConfig`. Bean `discountPolicy` của configuration do selector chọn có thể được resolve.

**`total200 = 180`**: `BusinessConfig` được import; Spring inject `CatalogRepository` và `DiscountPolicy` vào factory method. Giá 200 giảm 10% thành 180. Không cần `BusinessConfig` gọi trực tiếp method của `RepositoryConfig` hay `PromotionConfig`.

**`summary = catalog:10%`**: instance `CheckoutService` giữ chính hai dependency đã được BeanFactory chọn; record method trả chuỗi kết hợp thông tin từ repository và discount policy.

**`probe = ok`**: `HealthProbe` là regular class không có `@Component`. Nó tồn tại vì được **liệt kê tường minh trong `@Import`**, không phải nhờ quét package.

**`unscannedCandidates = 0`**: `UnscannedHelper` tuy có `@Component` nhưng không được scan/import. Đánh dấu stereotype trên source không tự đưa class thành bean.

**`sameRepository = true`**: repository có singleton scope mặc định trong context, nên bean lấy trực tiếp và reference mà `CheckoutService` nhận đều là cùng managed instance.

### 8.5. Bốn thay đổi nhỏ để luyện tập

**Thử A:** Xóa `RepositoryConfig.class` khỏi `@Import`. Nếu không có nguồn registration khác, `BusinessConfig.checkoutService(...)` không resolve được `CatalogRepository`. Context thường thất bại khi refresh do dependency bắt buộc không có; không thể tiếp tục đến stdout bình thường.

**Thử B:** Xóa `PromotionSelector.class` khỏi `@Import` nhưng không import `PromotionConfig` trực tiếp. `DiscountPolicy` không còn bean; tạo `CheckoutService` sẽ thất bại.

**Thử C:** Thay selector bằng `PromotionConfig.class` trong danh sách `@Import`. Output vẫn giữ nguyên vì bean `DiscountPolicy` được đăng ký trực tiếp từ một cấu hình thay vì thông qua selector.

**Thử D:** Thêm `UnscannedHelper.class` vào `@Import`. Trong context mới, số lượng tên bean kiểu `UnscannedHelper` sẽ trở thành 1 (nếu không có một đường registration khác tạo thêm). Đây là bằng chứng `@Import` cho phép đăng ký một regular component cụ thể mà không phải mở component scan.

## 9. `@Import` trong cấu hình nhiều module và các cấp truy cập

Trong dự án nhiều Gradle/Maven modules, một module có thể công bố configuration class như một **điểm vào công khai**:

```text
order-app
  -> @Import({OrdersModuleConfig.class, PaymentsModuleConfig.class})

orders-module
  -> OrdersModuleConfig (công bố các @Bean cần thiết)
  -> internal package (không cần scan toàn bộ)

payments-module
  -> PaymentsModuleConfig (công bố payment beans)
  -> internal package (không cần scan toàn bộ)
```

### 9.1. Module phải có trên runtime classpath

`@Import(PaymentsModuleConfig.class)` là một tham chiếu Java `Class<?>`. Build project phải có dependency tới module chứa type này. Nếu không compile được class hoặc module vắng khỏi runtime classpath, Spring không thể tự “tìm module ở đâu đó” chỉ từ tên annotation.

Ví dụ Gradle multi-project:

```groovy
dependencies {
    implementation project(':orders-module')
    implementation project(':payments-module')
}
```

Đây là đoạn minh họa cho build có hai subprojects đã được khai báo. Dependency đảm bảo classpath phù hợp; `@Import`/các cơ chế khác mới quyết định bean definitions nào tham gia container.

### 9.2. Không expose quá nhiều bean nội bộ

Một module library có thể công bố một `@Configuration` chứa các `@Bean` methods cho những services public, trong khi các implementation class private/internal không gắn stereotype hoặc không được scan rộng. Nhờ đó application biết entry-point cấu hình nhưng không cần biết tất cả implementation types.

Nếu imported config tự quét package nội bộ bằng `@ComponentScan`, phạm vi thực tế vẫn có thể rộng. Hãy xem composition root, import graph và filters để tránh đưa nhầm bean, profiles/test helpers hoặc alternate adapters vào ứng dụng.

### 9.3. Scope và vòng đời vẫn quyết định bởi BeanFactory

Import không thay đổi mặc định singleton của các `@Bean` methods, không tự kích hoạt lazy initialization, không tự tạo proxy AOP hoặc bật transaction. Nếu bean `A` được import và cần bean `B`, quan hệ phải được DI resolution đáp ứng đúng như mọi bean trong container. Cấu hình sẽ thất bại nếu dependency bắt buộc thiếu hoặc nếu có nhiều ứng viên mà không có một lựa chọn rõ.

### 9.4. Java Platform Module System (JPMS)

Nếu chạy theo `module-info.java` thay vì classpath thông thường, phản chiếu (reflection), constructor access hoặc CGLIB configuration enhancement có thể đòi `exports`/`opens` phù hợp. Lỗi `InaccessibleObjectException` khi instantiate một bean được import **khác** với tình huống bean definition không được nhập. Phân biệt việc import thành công với việc tạo bean thất bại.

## 10. Import có điều kiện và profiles

Một configuration class được nhập có thể mang `@Profile` hoặc `@Conditional`:

```java
@Configuration(proxyBeanMethods = false)
@Profile("dev")
class DevStorageConfig {

    @Bean
    Storage storage() {
        return new InMemoryStorage();
    }
}
```

Đây là đoạn minh họa cần định nghĩa `Storage`, `InMemoryStorage` và import `@Profile`. Nếu class được nhập nhưng profile `dev` không active, Spring có thể bỏ qua bean definitions của class theo quy tắc profile. `@Import` **không ép** mọi bean bên trong xuất hiện bất chấp các conditions.

Với một tập cấu hình cố định, cấu hình điều kiện trực tiếp trên imported configuration thường đơn giản hơn tự viết `ImportSelector`. Selector hợp lý khi cần đọc metadata annotation tùy chỉnh, chọn nhiều module implementation hoặc xây library/framework reusable.

### 10.1. `@Import` không xác lập thứ tự khởi tạo

`@Import({AConfig.class, BConfig.class})` không có nghĩa “hãy khởi tạo tất cả beans A rồi tới B”. BeanFactory vẫn tạo beans dựa trên dependency graph, scopes, lazy flags và lifecycle rules. Nếu service trong B cần repository của A, khai báo dependency bằng tham số `@Bean` hoặc constructor; nếu chỉ có side-effect order requirement, xem `@DependsOn` trong tình huống phù hợp.

### 10.2. Không nhầm `@Conditional` với exception handling

Nếu một imported bean không thỏa `@Conditional`, nó có thể không được đăng ký. Nếu một bean khác đang bắt buộc inject nó, container vẫn có thể báo lỗi thiếu dependency. `@Import` không tự cung cấp fallback; muốn optional module, bạn phải thiết kế `Optional<T>`, `ObjectProvider<T>`, conditional consumer bean hoặc fallback configuration phù hợp.

## 11. `@ImportResource`, XML và annotation `@Enable...`

### 11.1. Nhập bean definitions từ XML

`@ImportResource` hỗ trợ khai báo đường dẫn đến XML hoặc nguồn bean definition tương thích theo cơ chế tương ứng:

```java
@Configuration
@ImportResource("classpath:/legacy-services.xml")
class LegacyBridgeConfig {
}
```

Đây là đoạn minh họa, cần file `legacy-services.xml` hợp lệ và import `org.springframework.context.annotation.ImportResource`. **Không viết `@Import("classpath:/legacy-services.xml")`**, bởi `@Import` nhận class tokens, không nhận đường dẫn string resource.

### 11.2. Các annotation `@Enable...` thường dùng `@Import`

Trong nhiều extension của Spring, một annotation cấp cao bật hành vi bằng một hoặc nhiều imported configurations/selectors/registrars. Ví dụ `@EnableTransactionManagement`, `@EnableCaching` và `@EnableAsync` liên quan những infrastructure configurations khác nhau. Khi thấy một annotation `@EnableXxx`, có thể kiểm tra metadata `@Import` hoặc implementation extension để hiểu các bean definitions hạ tầng được kích hoạt.

**Điều này không có nghĩa** `@Import` tự mở transactions hoặc caching. Các annotation `@Enable...`, beans/aspects, proxies và điều kiện tương ứng mới phối hợp tạo hành vi.

### 11.3. `@Import` trên custom annotation

`@Import` cũng được hỗ trợ như một meta-annotation. Có thể định nghĩa:

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(PaymentsModuleConfig.class)
public @interface EnablePayments {
}
```

Đây là đoạn minh họa: cần imports từ `java.lang.annotation`, `@Import` và một `PaymentsModuleConfig` tồn tại. Consumer chỉ viết:

```java
@Configuration
@EnablePayments
class AppConfig {
}
```

Cách làm này tạo một **public API dạng annotation** cho module. Nếu chỉ có một điểm dùng nội bộ, `@Import(PaymentsModuleConfig.class)` trực tiếp có thể ít ceremony hơn.

## 12. Các lỗi thường gặp và cách chẩn đoán

| Triệu chứng | Khả năng nguyên nhân | Cách xử lý |
| --- | --- | --- |
| `NoSuchBeanDefinitionException` với bean tưởng đã import | Imported config không được xử lý, bị `@Profile`/`@Conditional` bỏ qua hoặc không có `@Bean` phù hợp | Kiểm tra entry-point context, import graph, profiles, conditions |
| `ClassNotFoundException`/compile error cho config được import | Module dependency/classpath sai | Kiểm tra build/runtime dependencies, class/package names |
| Imported config có mặt nhưng service tạo thất bại | Factory method cần dependency mà context không có | Xem chữ ký `@Bean`, qualifier và nguồn bean definition |
| Hai `@Bean` methods của hai configs trùng tên `service` | Bean names xung đột hoặc bị override | Đặt tên tường minh khác nhau, rà soát module ownership |
| Có nhiều bean cùng type sau khi import module thứ hai | Dependency đơn không thể chọn ứng viên duy nhất | Dùng `@Qualifier`/`@Primary` hoặc thiết kế injection tập hợp |
| `@Import(SomeClass.class)` không kéo toàn bộ package | `@Import` đăng ký class cụ thể; không scan package | Dùng `@ComponentScan` nếu thực sự muốn quét nhóm class |
| `@Component` không tự xuất hiện khi thiếu scan | Chưa import hoặc scan component | Import class cụ thể, hoặc bật component scanning đúng root |
| Dùng `@Import("config.xml")` bị lỗi cú pháp | `@Import` yêu cầu `Class<?>` | Dùng `@ImportResource` cho XML |
| Selector không nhập được class | `selectImports` trả bean name thay vì FQ class name | Trả tên class đầy đủ (`SomeConfig.class.getName()`) |
| Conditional imported config bị bỏ qua | Profile/condition không phù hợp | Kiểm tra active profile và `@Conditional` |
| Dùng `@Import` rồi chờ A tạo trước B theo thứ tự mảng | Nhầm import order với dependency order | Dùng tham số factory/constructor, `@DependsOn` khi phù hợp |
| Registrar tạo bean name trùng | Registrations đang tranh một registry key | Kiểm tra registry name, generation policy, duplication |
| Registry post-processor được đăng ký sai giai đoạn | Lạm dụng `ImportBeanDefinitionRegistrar` cho `BeanDefinitionRegistryPostProcessor` | Dùng lifecycle extension point đúng theo tài liệu Spring |
| Gọi `otherConfig.bean()` tạo instance ngoài quản lý | Gọi method Java trực tiếp, đặc biệt lite `@Configuration` | Truyền dependency qua factory-method parameters |
| Classpath chứa module nhưng bean vắng mặt | JAR hiện diện không đồng nghĩa bean được import/scan | Import configuration entry point, scan đúng package hoặc auto-config |
| Test module A chạy, app thực không có bean A | Hai contexts có entry-points/import graphs khác nhau | Kiểm tra từng context và cấu hình test thực sự dùng |

### 12.1. Quy trình debug thực dụng

**Bước 1 — Kiểm tra configuration entry point.** Một class `@Configuration` có `@Import` phải thực sự được đăng ký, quét hoặc nhập bởi một configuration khác. Annotation nằm trên một class không bao giờ được Spring biết đến sẽ không có tác dụng.

**Bước 2 — Vẽ import graph.** Ghi rõ từ `AppConfig` đi tới class cấu hình nào, selector trả class nào, registrar có tạo definitions nào. Phân biệt imports trực tiếp và imports bắc cầu.

**Bước 3 — Kiểm tra bean type/name.** Nếu context chạy được:

```java
String[] names = context.getBeanNamesForType(CheckoutService.class);
System.out.println(java.util.Arrays.toString(names));
```

Đây là đoạn minh họa khi `context` và `CheckoutService` có sẵn. Tránh đoán tên imported configuration class: bean names được tạo bởi configuration registration mechanism, có thể khác component scan naming.

**Bước 4 — Kiểm tra điều kiện và lifecycle.** Bean có thể bị profile/condition loại trước khi đăng ký, hoặc được đăng ký rồi lỗi khi tạo. Đọc root cause của `BeanCreationException`/`UnsatisfiedDependencyException` để xác định giai đoạn.

**Bước 5 — Kiểm tra xung đột.** Nếu thêm module làm context lỗi, tìm những bean names trùng hoặc interface injection trước đây chỉ có một bean nhưng nay có hai. Đừng bật bean definition overriding toàn cục chỉ để che xung đột chưa hiểu.

**Bước 6 — Dùng test context nhỏ.** Như mục 8, đăng ký chỉ `AppConfig.class` và xác minh từng bean theo type. Dùng điều kiện/profiles giống môi trường mục tiêu khi behavior phụ thuộc chúng.

## 13. Bài tập thực hành kèm đáp án

### Bài 1 — Một hay hai configuration classes khi khởi động?

Cho `@Configuration @Import(StorageConfig.class) class AppConfig` và `@Configuration class StorageConfig` có một `@Bean Storage storage()`. Khi khởi tạo context bằng `new AnnotationConfigApplicationContext(AppConfig.class)`, có cần truyền thêm `StorageConfig.class` không?

**Đáp án:** Không. `AppConfig` đã import `StorageConfig`. Spring xử lý bean definitions do `StorageConfig` cung cấp.

### Bài 2 — Import một regular class

Một class `HealthCheck` không có `@Component`, có public no-arg constructor. `@Configuration @Import(HealthCheck.class) class AppConfig` được đăng ký. Có thể lookup `HealthCheck` theo type không?

**Đáp án:** Có, khi quá trình import và bean creation thành công. Spring 6.2 hỗ trợ import regular component classes mà không bắt buộc có component scanner.

### Bài 3 — Component bên cạnh class được import

`EmailGateway` và `SmsGateway` ở cùng package; chỉ có `@Import(EmailGateway.class)`, không có scanner. `SmsGateway` có trở thành bean vì nằm cùng package không?

**Đáp án:** Không qua import đó. `@Import` đăng ký những class/cấu hình nêu cụ thể, không quét sibling classes.

### Bài 4 — Phụ thuộc xuyên config

`ServiceConfig` có `@Bean OrderService orderService(OrderRepository repository)`, còn `RepositoryConfig` có `@Bean OrderRepository orderRepository()`. Cần gọi method `repositoryConfig.orderRepository()` trực tiếp không?

**Đáp án:** Không. Import/register cả hai config trong cùng context và để Spring giải quyết tham số `OrderRepository` bằng BeanFactory.

### Bài 5 — ImportSelector trả gì?

`ImportSelector.selectImports(...)` trả `new String[]{"paymentService"}`, trong đó `paymentService` chỉ là bean name, không phải class FQCN. Có đúng không?

**Đáp án:** Không. Selector cần trả các tên class import candidates hợp lệ; ví dụ `new String[]{PaymentsConfig.class.getName()}`.

### Bài 6 — @Bean trùng tên

Module A và B đều khai báo `@Bean PaymentClient client()`, có import cả hai. Điều gì đáng lo?

**Đáp án:** Hai bean definitions mặc định cùng tên `client`. Tùy chính sách registry, có thể đụng tên/ghi đè hoặc khởi tạo sai mong đợi. Đặt tên có chủ đích và tránh đăng ký chồng chéo.

### Bài 7 — Profile chưa active

`@Import(DevMailConfig.class)` nhưng `DevMailConfig` có `@Profile("dev")`, môi trường hiện tại chỉ active profile `prod`. Có chắc các `@Bean` của config vẫn tồn tại không?

**Đáp án:** Không. `@Profile` có thể khiến config bị bỏ qua. Import không vô hiệu hóa profile conditions.

### Bài 8 — XML config

Một ứng dụng legacy còn file `services.xml`. Dùng `@Import(services.xml)` hay `@ImportResource("classpath:/services.xml")`?

**Đáp án:** Dùng `@ImportResource` cho resource XML. `@Import` dành cho Java class candidates/selectors/registrars.

### Bài 9 — Import trùng để tạo hai bean

Hai cấu hình cùng import một `StorageConfig` chứa singleton `StorageClient`. Có thể dùng điều đó để tạo hai `StorageClient` độc lập không?

**Đáp án:** Không nên và không thể dựa vào đó. Spring xử lý configuration candidates trong registry; muốn hai clients cần hai bean definitions khác nhau (ví dụ hai `@Bean` methods với tên/parameters khác).

### Bài 10 — Thử thay selector trong demo

Trong mục 8, xóa `PromotionSelector.class` và thêm `PromotionConfig.class` vào `@Import`. Đầu ra `discount = 10%` và `total200 = 180` có thay đổi không?

**Đáp án:** Không trong cấu hình demo, vì cả hai đường đều nhập `PromotionConfig`. Selector chỉ thêm logic lựa chọn, không thay đổi bean contract của `DiscountPolicy`.

### Bài 11 — Registrar hay @Bean?

Một application cần chính xác hai beans `Clock` và `ReceiptFormatter` với implementations cố định. Có nhất thiết viết `ImportBeanDefinitionRegistrar`?

**Đáp án:** Không. Một `@Configuration` với hai `@Bean` methods rõ ràng đơn giản, minh bạch hơn. Registrar nên dành cho hạ tầng tạo definitions dựa trên metadata/quy tắc động.

### Bài 12 — Thay component scanning bằng module API

Một library có 50 implementation classes, app chỉ cần 3 beans public mà library config đã công bố. Nên mở rộng `@ComponentScan` sang toàn bộ package nội bộ hay nhập configuration public?

**Đáp án:** Khi library đã thiết kế entry-point đúng, nhập configuration public thường rõ ranh giới hơn. Tuy nhiên phải kiểm tra configuration đó có quét tiếp package nội bộ hay import auto-config nào khác không.

## 14. Checklist thiết kế `@Import` an toàn

- Cấu hình root/entry-point có **thực sự được context đăng ký** chưa?
- Có thể liệt kê rõ những module configurations cần tham gia không? Nếu có, `@Import({AConfig.class, BConfig.class})` thường đủ.
- Imported class là `@Configuration`, regular component, selector hay registrar? Đừng nhầm cơ chế mỗi loại.
- Các `@Bean` methods của nhiều configurations có **trùng bean name/type** không?
- Dependency xuyên config đã được thể hiện bằng factory-method parameters hoặc constructors chưa?
- Imported configuration có `@ComponentScan`, `@Import` bắc cầu, `@Profile`, `@Conditional` hoặc lifecycle extension đáng chú ý không?
- Nếu dùng selector, đã trả **tên class đầy đủ** và kiểm tra các điều kiện/annotation attributes hợp lệ chưa?
- Nếu dùng registrar, đã kiểm soát bean names và không vi phạm hạn chế lifecycle của `BeanDefinitionRegistryPostProcessor` chưa?
- Có đang cần XML? Nếu có, dùng `@ImportResource` thay vì ép resource vào `@Import`.
- Module thực sự có trên runtime classpath không? Với JPMS, đã xử lý access/reflection khi cần chưa?
- Test integration tạo context từ **một composition root** đã kiểm tra được những bean được import và những bean cố ý không đăng ký chưa?

## 15. Tổng kết

`@Import` là công cụ quan trọng để **ghép Java-based configurations một cách tường minh** trong Spring Core Container. Bạn có thể nhập một/một số `@Configuration` classes, regular component classes hoặc các extension points `ImportSelector` và `ImportBeanDefinitionRegistrar`. Spring xử lý metadata/import graph để đăng ký bean definitions; BeanFactory vẫn quyết định dependency injection, scope và lifecycle.

Nên dùng `@Import` khi muốn composition root rõ ràng, phân mô-đun và chỉ kích hoạt những cấu hình cần thiết. Với nhiều component ứng dụng cần tự phát hiện theo package, `@ComponentScan` có thể phù hợp hơn. Với cấu hình XML, chọn `@ImportResource`. Khi bean A cần bean B từ config khác, ưu tiên **method-parameter injection** thay vì gọi `@Bean` method trực tiếp và phụ thuộc vào proxy mode.

**Quy tắc ghi nhớ:** import chọn *nguồn bean definitions*, không tự quét package, không bảo đảm bean creation order và không vượt qua profile/conditional/dependency rules. Một đồ thị import gọn và có test sẽ dễ bảo trì hơn việc scan thật rộng rồi xử lý các bean ngoài ý muốn.

### Tài liệu tham khảo chính thức

- [Spring Framework 6.2 — Composing Java-based Configurations](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/composing-configuration-classes.html).
- [Spring Framework 6.2 — `@Import` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/context/annotation/Import.html).
- [Spring Framework 6.2 — `ImportSelector` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/context/annotation/ImportSelector.html).
- [Spring Framework 6.2 — `ImportBeanDefinitionRegistrar` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/context/annotation/ImportBeanDefinitionRegistrar.html).
- [Spring Framework 6.2 — `@ImportResource` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/context/annotation/ImportResource.html).
- [Spring Framework — Creating an ApplicationContext with Annotation-based Config](https://docs.spring.io/spring-framework/reference/core/beans/java/instantiating-container.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="configuration-composition-003">Kết hợp nhiều nguồn cấu hình trong một context</a>

<details>
<summary>Xem chi tiết</summary>

# Kết hợp nhiều nguồn cấu hình trong một Spring ApplicationContext

## 1. Đặt vấn đề

Trong một dự án Spring Framework, không phải tất cả bean đều đến từ cùng một file hoặc cùng một cách khai báo. Một ứng dụng đang phát triển có thể có:

- Những bean do ứng dụng tạo bằng **Java configuration** (`@Configuration` và `@Bean`).
- Những bean còn giữ trong **XML configuration** của hệ thống lâu năm.
- Những service/adapters được phát hiện qua **component scanning** (`@ComponentScan` cùng `@Component`, `@Service`, `@Repository`,...).
- Những bean được đăng ký **bằng chương trình** khi bootstrap (`register`, `registerBean`, `registerBeanDefinition`).
- Các cấu hình nhỏ được ghép qua **`@Import`**, hoặc nguồn XML được kết nối bằng **`@ImportResource`**.

Spring có thể đưa những **bean definitions** này vào **một IoC Container**, rồi giải quyết dependency giữa chúng mà không bắt buộc tất cả class cùng sử dụng một annotation.

Ý nghĩa của chủ đề không chỉ là “nạp nhiều file”: điều cần hiểu là **nguồn cấu hình đóng góp metadata**, còn **một BeanFactory/ApplicationContext** dùng metadata đó để tạo và quản lý object theo quy tắc chung. Kết quả là một bean từ XML có thể được inject vào service được component scan, và một bean từ `@Bean` có thể cùng sử dụng bean đăng ký chương trình.

### Mục tiêu sau bài học

Bạn sẽ biết cách:

1. Phân biệt **nguồn bean definitions** với **nguồn properties** và với **nhiều ApplicationContext riêng biệt**.
2. Ghép Java configuration bằng `@Import` và XML bằng `@ImportResource`.
3. Kết hợp `@ComponentScan` với các bean `@Bean`, XML và đăng ký bằng `registerBean`.
4. Tạo context theo đúng trình tự **đăng ký nguồn → `refresh()` → sử dụng → `close()`**.
5. Giải quyết dependency xuyên các nguồn; xử lý bean cùng tên, nhiều ứng viên cùng kiểu, thứ tự khởi tạo và processor.
6. Chọn chiến lược tích hợp XML cũ với Java configuration mới mà không vô tình tạo context lồng nhau.
7. Chạy một ví dụ Java 17+ với **bốn cách đăng ký bean khác nhau** cùng hoạt động trong **một `AnnotationConfigApplicationContext`**.

## 2. Khái niệm trung tâm: một context, nhiều nguồn metadata

Spring bean là một object được container quản lý theo bean definition/registration. Container cần biết bean class hoặc supplier/factory, tên, scope, phụ thuộc và các thuộc tính quản lý khác. Metadata ấy có thể đến từ nhiều nơi.

```text
Java @Configuration/@Bean ─────┐
                              │
XML <bean> / @ImportResource ──┤
                              │
@ComponentScan ────────────────┼──> BeanDefinitionRegistry
                              │             │
registerBean(...) ────────────┘             v
                                     BeanFactory / Context
                                              │
                                Dependency resolution + lifecycle
                                              │
                                       Bean instances
                                              │
                                  Một ApplicationContext
```

**Một context** nghĩa là những registrations cùng đóng góp vào **một registry/factory và cùng cơ chế giải quyết dependency**. Bean singleton mặc định là singleton **trong container đó**, không phải singleton toàn JVM.

Ví dụ:

- `Formatter` đến từ Java `@Bean`.
- `LegacyPrefixer` đến từ XML `<bean>`.
- `AggregationService` đến từ `@ComponentScan`.
- `AuditSink` đến từ `context.registerBean(...)`.

Khi `AggregationService` có constructor cần cả ba dependency, Spring tìm ứng viên trong cùng context và inject chúng; service **không cần biết** mỗi dependency đã được khai báo bằng hình thức nào.

### Không nhầm “nhiều nguồn” với “nhiều container”

Nếu bạn tạo:

```java
var context1 = new AnnotationConfigApplicationContext(JavaConfig.class);
var context2 = new ClassPathXmlApplicationContext("legacy.xml");
```

đây là **hai ApplicationContext riêng**, không tự chia sẻ registry hoặc singleton cache. Để lấy bean từ context khác, phải thiết lập quan hệ parent-child hoặc kết nối có chủ đích. Điều đó **khác** với việc Java và XML cùng đóng góp bean definitions vào **một** context.

Đoạn trên là snippet minh họa; cần imports và `JavaConfig`, `legacy.xml` thực tế. Đừng dùng nó nếu mục tiêu là ghép bean vào cùng một context.

## 3. Năm cách đóng góp cấu hình thường gặp

| Cách | Ví dụ | Tác dụng | Tình huống phù hợp |
| --- | --- | --- | --- |
| Java factory method | `@Bean Formatter formatter()` | Đăng ký bean từ factory method | Object thư viện, cấu hình rõ ràng, builder |
| Java configuration import | `@Import(PricingConfig.class)` | Nạp thêm configuration/type | Ghép module chức năng |
| XML import | `@ImportResource("classpath:legacy.xml")` | Nạp XML bean definitions vào context Java | Tích hợp XML cũ, XML namespace đặc biệt |
| Component scanning | `@ComponentScan(basePackageClasses=JobService.class)` | Tìm class mang stereotype/filter | Service, adapter và component tự quản lý source |
| Programmatic registration | `context.registerBean("auditSink", AuditSink.class, AuditSink::new)` | Đăng ký bean theo API trước refresh | Bootstrap động, fixture test, plugin/module runtime |

Các cách này **có thể phối hợp**. Tuy nhiên, một bean nên có **registration owner rõ ràng**; không được mặc định rằng khai báo trùng một class theo nhiều nguồn sẽ được Spring tự “gộp” thành một bean.

## 4. Cách 1 — Java @Configuration và @Bean

Java configuration biểu diễn nguồn bean definitions bằng source Java có type checking:

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class FormattingConfiguration {
    @Bean
    Formatter formatter() {
        return new Formatter("java");
    }
}
```

Đây là cấu hình rút gọn, giả định `Formatter` là class trong dự án có constructor nhận `String`. Trong demo hoàn chỉnh bên dưới, class đó sẽ được khai báo thật.

Khi `FormattingConfiguration` được nạp qua `@Import` hoặc `register`, Spring nhận diện factory method và đăng ký bean tên `formatter`. Khi cần tạo object, Spring gọi method và quản lý kết quả theo scope/lifecycle.

**Đặc điểm:**

- Return type của `@Bean` giúp Spring dự đoán type để giải quyết dependency.
- Factory method có thể nhận bean dependency thông qua tham số: `Formatter formatter(AppSettings settings)`.
- Không cần `@Component` trên class object trả về.
- `proxyBeanMethods=false` không tắt `@Bean`; nó chỉ tắt inter-bean method interception trong configuration class đó. Dùng method parameter injection để ghép bean, tránh gọi chéo factory methods theo cách tạo object phụ.
- Một class có `@Configuration` trong source code vẫn cần được **nạp vào context** bằng đường hợp lệ; annotation tự nó không tạo context.

## 5. Cách 2 — @Import ghép nhiều Java configuration

Giả sử module báo cáo khai báo bean, còn module định dạng cung cấp dependency:

```java
@Configuration(proxyBeanMethods = false)
@Import(FormattingConfiguration.class)
public class ReportsConfiguration {
    @Bean
    ReportService reportService(Formatter formatter) {
        return new ReportService(formatter);
    }
}
```

`ReportService` và `Formatter` là kiểu ví dụ; đoạn này đặt trong class có imports tương ứng. Khi `ReportsConfiguration` được register, Spring xử lý `@Import` rồi đăng ký definitions từ `FormattingConfiguration`.

Cách này có hai lợi ích chính:

1. **Composition rõ ràng:** chỉ nạp cấu hình gốc, các nguồn con được import theo thiết kế.
2. **Dependency tường minh:** method `reportService(Formatter formatter)` nói cần bean `Formatter`; không gọi `new FormattingConfiguration().formatter()`.

`@Import` không tạo context con. Hai bean cùng nằm trong container đang được xây dựng. Một ứng dụng cũng có thể dùng `context.register(ConfigA.class, ConfigB.class)` thay vì cấu hình import tập trung; hai cách là những con đường nạp configuration metadata, không phải hai loại bean khác nhau.

## 6. Cách 3 — XML bean definitions qua @ImportResource

Trong hệ thống đang di chuyển từ XML sang Java configuration, Spring cho phép giữ một số bean trong XML và nạp vào context Java bằng `@ImportResource`.

**Java configuration:**

```java
@Configuration(proxyBeanMethods = false)
@ImportResource("classpath:legacy-beans.xml")
public class ApplicationConfiguration {
}
```

**`src/main/resources/legacy-beans.xml`:**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd">

    <bean id="legacyPrefixer" class="demo.legacy.LegacyPrefixer">
        <constructor-arg value="xml"/>
    </bean>
</beans>
```

Class `demo.legacy.LegacyPrefixer` phải tồn tại trên classpath và có constructor nhận `String`. File XML thuộc **classpath resource**, được Gradle đặt vào runtime classpath từ `src/main/resources`.

**Khi nào có lợi?**

- Có nhiều definitions XML cũ chưa muốn chuyển toàn bộ.
- Một thư viện/framework tích hợp bằng XML namespace tiện hơn Java config.
- Muốn triển khai theo kiểu Java-centric: cấu hình mới viết Java, phần legacy chỉ import nơi cần.

`@ImportResource` giúp **XML và Java cùng góp definitions vào một context**; không tạo một `ClassPathXmlApplicationContext` khác ở hậu trường cho từng file import.

### 6.1. Java-centric hay XML-centric?

**Java-centric** dùng `AnnotationConfigApplicationContext` làm điểm khởi động và `@ImportResource` nạp bean definitions XML vào **cùng context**. **XML-centric** có thể dùng `ClassPathXmlApplicationContext`, khai báo Java configuration classes trong XML và bật annotation configuration processing phù hợp (chẳng hạn `<context:annotation-config/>`). Hai cách đều hợp lệ; không tự mở hai context độc lập rồi giả định chúng chia sẻ beans.

### 6.2. Khác biệt với XML `<import/>`

XML `<import resource="feature-beans.xml"/>` nạp thêm XML từ resource hiện tại; `@ImportResource("classpath:...")` nạp XML từ Java configuration; `@Import(SomeConfig.class)` nạp class/selector/registrar qua Java metadata. Trong bài tổng hợp này, cả ba là **nguồn bean definitions cho cùng container**. Cách khai báo đường dẫn, namespace, alias và lộ trình chuyển đổi XML được thực hành riêng ở bài `@ImportResource` tiếp theo.

## 7. Cách 4 — @ComponentScan và stereotype

Một class do ứng dụng quản lý source có thể mang `@Component`:

```java
package demo.scanned;

import org.springframework.stereotype.Component;

@Component
public final class HealthMarker {
    public String label() {
        return "scanned";
    }
}
```

Một configuration class có:

```java
@ComponentScan(basePackageClasses = HealthMarker.class)
```

sẽ scan **package chứa `HealthMarker` và các package con**, không chỉ riêng `HealthMarker`. Scanner mặc định nhận `@Component` và các stereotype/meta-annotations phù hợp.

Nhiều nguồn cấu hình kết hợp hữu ích vì class được scan **có thể inject** bean từ XML, `@Bean` hay registration API. Ví dụ:

```java
@Component
public final class ReportCoordinator {
    private final Formatter formatter;
    private final LegacyPrefixer prefixer;

    public ReportCoordinator(Formatter formatter, LegacyPrefixer prefixer) {
        this.formatter = formatter;
        this.prefixer = prefixer;
    }

    public String format(String value) {
        return prefixer.wrap(formatter.format(value));
    }
}
```

Đây là một class minh họa (cần package/import các kiểu), không phải file độc lập của demo chính. Constructor duy nhất thường được Spring tự chọn để inject mà không cần `@Autowired`. Quan trọng là **hai dependency không nhất thiết được scan**: chúng có thể đến từ Java hoặc XML.

### 7.1. Tại sao nên giới hạn phạm vi scan?

Nếu scan package gốc quá rộng, Spring có thể tìm thêm:

- Nhiều `@Configuration` ngoài ý định, kéo theo `@Bean` definitions khác.
- Một implementation dự kiến chỉ dùng trong test.
- Các component cùng bean name hoặc cùng interface dẫn đến trùng/mơ hồ.
- Component của module không muốn bật.

Vì vậy `basePackageClasses` hoặc `basePackages` nên gắn với ranh giới phù hợp. `@Import` ở composition root giúp thấy rõ modules, `@ComponentScan` giúp module chọn các class do nó sở hữu; cả hai không mâu thuẫn.

## 8. Cách 5 — Đăng ký bằng chương trình trước refresh()

Spring cho phép đăng ký bean bằng Java API:

```java
AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext();

context.registerBean(
        "auditSink",
        AuditSink.class,
        AuditSink::new
);

context.register(AppConfiguration.class);
context.refresh();
```

`AppConfiguration` và `AuditSink` là các kiểu minh họa; cần import `AnnotationConfigApplicationContext`. API `registerBean` nhận tên, bean class và `Supplier`, tạo bean definition để container sử dụng khi khởi tạo.

**Không nhầm hai cách:**

- `context.registerBean("auditSink", AuditSink.class, AuditSink::new)`: đăng ký **bean definition có supplier**; Spring quản lý instance được tạo theo definition.
- `AuditSink sink = new AuditSink()` ở một biến Java bên ngoài: chỉ tạo object Java. Nó không tự trở thành Spring bean và không tự được inject/callback khi chưa đăng ký theo API phù hợp.

### 8.1. Vì sao phải register trước refresh?

`AnnotationConfigApplicationContext` có constructor rỗng để bạn thêm cấu hình bằng `register(...)`, `scan(...)` và `registerBean(...)`, rồi **gọi `refresh()`** cho Spring xử lý metadata/processor và tạo các singleton cần thiết.

Thứ tự bootstrap khuyến nghị:

```text
new AnnotationConfigApplicationContext()
   |
register(AppConfiguration.class)
registerBean("auditSink", AuditSink.class, AuditSink::new)
   |
refresh()
   |
getBean(AggregationService.class)
   |
close()
```

Trong một số biến thể container có API cho phép đăng ký definition sau khi đã refresh, nhưng **đừng dùng đó làm hành vi mặc định** khi bài toán là bootstrap nhiều nguồn: post-processor, singleton pre-instantiation, event/lifecycle và điều kiện cấu hình có thể đã chạy. Hãy hoàn tất registrations trước `refresh()` nếu muốn một tập bean definitions được xử lý nhất quán từ đầu.

### 8.2. registerBean khác register(Class...) và scan(String...)

- `register(AppConfiguration.class)`: đăng ký một **annotated component/configuration class** để Spring xử lý metadata của class ấy.
- `scan("demo.scanned")`: yêu cầu scanner tìm candidate trong package.
- `registerBean("auditSink", AuditSink.class, AuditSink::new)`: đăng ký một bean definition trực tiếp, không cần class có `@Component` hoặc scan.
- `registerBeanDefinition(...)` trên registry: API cấp thấp, cần cung cấp BeanDefinition phù hợp; thường chỉ cần cho framework/integration code.

Chúng là những cách khác nhau để **góp registrations vào cùng context**.

## 9. Ví dụ chạy được: Java + XML + component scan + programmatic bean

Đây là ví dụ trung tâm. Ta cần bốn bean từ bốn nguồn khác nhau:

| Bean | Class | Nguồn |
| --- | --- | --- |
| `formatter` | `Formatter` | `@Bean` từ Java configuration được `@Import` |
| `legacyPrefixer` | `LegacyPrefixer` | XML `<bean>` được `@ImportResource` |
| `aggregationService` | `AggregationService` | `@Component` được `@ComponentScan` |
| `auditSink` | `AuditSink` | `context.registerBean(...)` tại bootstrap |

`AggregationService` có constructor nhận **cả ba dependency**: `Formatter`, `LegacyPrefixer`, `AuditSink`. Spring phải tìm các bean theo type trong **một context** để khởi tạo được service.

### 9.1. Cấu trúc dự án

```text
multi-source-context-demo/
├── settings.gradle
├── build.gradle
└── src/
    └── main/
        ├── java/
        │   └── demo/
        │       ├── App.java
        │       ├── config/
        │       │   ├── AppConfiguration.java
        │       │   └── FormattingConfiguration.java
        │       ├── javaformat/
        │       │   └── Formatter.java
        │       ├── legacy/
        │       │   └── LegacyPrefixer.java
        │       ├── audit/
        │       │   └── AuditSink.java
        │       └── scanned/
        │           └── AggregationService.java
        └── resources/
            └── legacy-beans.xml
```

Mỗi public top-level class đặt trong file trùng tên với class và package khớp đường dẫn.

### 9.2. Gradle configuration

**`settings.gradle`**

```groovy
rootProject.name = 'multi-source-context-demo'
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
    mainClass = 'demo.App'
}
```

Spring Context đã mang theo những dependency Spring Core/Beans cần thiết để xử lý XML và annotation configuration. Ví dụ chạy bằng Java 17+, không cần Spring Boot, servlet container hay database.

### 9.3. Bean Java: Formatter

**`src/main/java/demo/javaformat/Formatter.java`**

```java
package demo.javaformat;

import java.util.Objects;

public final class Formatter {
    private final String source;

    public Formatter(String source) {
        this.source = Objects.requireNonNull(source);
    }

    public String format(String value) {
        return source + "[" + value + "]";
    }
}
```

Class này **không có `@Component`**. Nó trở thành Spring bean nhờ factory method Java configuration.

**`src/main/java/demo/config/FormattingConfiguration.java`**

```java
package demo.config;

import demo.javaformat.Formatter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class FormattingConfiguration {
    @Bean
    Formatter formatter() {
        return new Formatter("java");
    }
}
```

Bean name mặc định là `formatter`; return type là `Formatter`. Spring có thể inject nó cho service nằm ở package khác.

### 9.4. Bean XML: LegacyPrefixer

**`src/main/java/demo/legacy/LegacyPrefixer.java`**

```java
package demo.legacy;

import java.util.Objects;

public final class LegacyPrefixer {
    private final String prefix;

    public LegacyPrefixer(String prefix) {
        this.prefix = Objects.requireNonNull(prefix);
    }

    public String wrap(String input) {
        return prefix + "(" + input + ")";
    }
}
```

Không có stereotype hay Spring annotation.

**`src/main/resources/legacy-beans.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd">

    <bean id="legacyPrefixer" class="demo.legacy.LegacyPrefixer">
        <constructor-arg value="xml"/>
    </bean>
</beans>
```

Bean XML tên `legacyPrefixer` có constructor nhận chuỗi `xml`. XML là nguồn definition thứ hai, không phải một ApplicationContext độc lập.

### 9.5. Bean programmatic: AuditSink

**`src/main/java/demo/audit/AuditSink.java`**

```java
package demo.audit;

import java.util.concurrent.atomic.AtomicInteger;

public final class AuditSink {
    private final AtomicInteger records = new AtomicInteger();

    public String record(String message) {
        int number = records.incrementAndGet();
        return "AUDIT#" + number + ":" + message;
    }

    public int count() {
        return records.get();
    }
}
```

Class không mang stereotype. Chúng ta **không** viết `@Bean AuditSink` trong Java config và **không** khai báo nó trong XML. Entry point sẽ trực tiếp gọi API `registerBean` trước `refresh()`.

### 9.6. Bean component scan: AggregationService

**`src/main/java/demo/scanned/AggregationService.java`**

```java
package demo.scanned;

import demo.audit.AuditSink;
import demo.javaformat.Formatter;
import demo.legacy.LegacyPrefixer;

import org.springframework.stereotype.Component;

@Component
public final class AggregationService {
    private final Formatter formatter;
    private final LegacyPrefixer prefixer;
    private final AuditSink audit;

    public AggregationService(
            Formatter formatter,
            LegacyPrefixer prefixer,
            AuditSink audit) {
        this.formatter = formatter;
        this.prefixer = prefixer;
        this.audit = audit;
    }

    public String execute(String input) {
        return audit.record(
                prefixer.wrap(
                        formatter.format(input)
                )
        );
    }
}
```

Có **một constructor** cần ba dependency. Spring Framework 6.2 có thể constructor-inject constructor duy nhất dù không đặt `@Autowired`. Container sẽ tìm bean `Formatter`, `LegacyPrefixer` và `AuditSink` sau khi tất cả nguồn metadata đã được nạp.

Class được annotate `@Component` nên sẽ được scanner chọn, **nhưng scanner không tự tạo thêm ba dependency**. Chúng đến từ ba nguồn khác.

### 9.7. Composition root Java: import Java + XML + scan

**`src/main/java/demo/config/AppConfiguration.java`**

```java
package demo.config;

import demo.scanned.AggregationService;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportResource;

@Configuration(proxyBeanMethods = false)
@Import(FormattingConfiguration.class)
@ImportResource("classpath:legacy-beans.xml")
@ComponentScan(basePackageClasses = AggregationService.class)
public class AppConfiguration {
}
```

Ba annotations ghép các nguồn:

1. `@Import(FormattingConfiguration.class)`: nạp `@Bean Formatter` từ module Java.
2. `@ImportResource("classpath:legacy-beans.xml")`: nạp XML legacy bean.
3. `@ComponentScan(basePackageClasses = AggregationService.class)`: quét package `demo.scanned`, nhận `AggregationService`.

`AppConfiguration` **không đăng ký `AuditSink`**, vì nguồn programmatic sẽ đăng ký ở bootstrap. Tách việc đó ra để nhìn rõ tác dụng của API.

### 9.8. Entry point: một ApplicationContext duy nhất

**`src/main/java/demo/App.java`**

```java
package demo;

import demo.audit.AuditSink;
import demo.config.AppConfiguration;
import demo.scanned.AggregationService;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public final class App {
    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {

            context.register(AppConfiguration.class);
            context.registerBean(
                    "auditSink",
                    AuditSink.class,
                    AuditSink::new
            );

            context.refresh();

            System.out.println("java-bean="
                    + context.containsBean("formatter"));
            System.out.println("xml-bean="
                    + context.containsBean("legacyPrefixer"));
            System.out.println("scanned-bean="
                    + context.containsBean("aggregationService"));
            System.out.println("programmatic-bean="
                    + context.containsBean("auditSink"));

            AggregationService service =
                    context.getBean(AggregationService.class);
            System.out.println("result=" + service.execute("ok"));

            AuditSink audit = context.getBean(AuditSink.class);
            System.out.println("audit-count=" + audit.count());
            System.out.println("same-audit="
                    + (audit == context.getBean("auditSink", AuditSink.class)));
        }
    }
}
```

Không có lệnh tạo `ClassPathXmlApplicationContext` riêng. Bốn nguồn cùng đi vào `context` có một `refresh()` và một lần `close()`. `try-with-resources` đảm bảo context được đóng sau khi sử dụng; với tài nguyên thật, Spring có thể gọi destruction callbacks phù hợp.

### 9.9. Chạy và kết quả

Từ thư mục `multi-source-context-demo`:

```bash
gradle run --quiet
```

Nếu dự án có Gradle Wrapper, dùng `./gradlew run --quiet` (Linux/macOS) hoặc `gradlew.bat run --quiet` (Windows). Lệnh `javac App.java` đơn lẻ không đủ vì ứng dụng có nhiều source files và cần Spring dependencies trên classpath; Gradle giải quyết cả hai.

**Các dòng do chương trình in ra:**

```text
java-bean=true
xml-bean=true
scanned-bean=true
programmatic-bean=true
result=AUDIT#1:xml(java[ok])
audit-count=1
same-audit=true
```

Log Gradle hoặc logging runtime, nếu có, là thông tin môi trường khác với bảy dòng `System.out.println` kể trên.

### 9.10. Vì sao output chính xác?

**`java-bean=true`:** cấu hình `FormattingConfiguration` được `@Import` nạp, factory method `@Bean` đăng ký tên `formatter`.

**`xml-bean=true`:** `@ImportResource` nạp `legacy-beans.xml` và đăng ký `legacyPrefixer`.

**`scanned-bean=true`:** `@ComponentScan` quét package chứa `AggregationService` và scanner chọn class `@Component`, tên bean thông thường là `aggregationService`.

**`programmatic-bean=true`:** `context.registerBean("auditSink", ...)` đăng ký bean definition với supplier trước refresh.

**`result=AUDIT#1:xml(java[ok])`:** `formatter.format("ok")` trả `java[ok]`; `LegacyPrefixer.wrap(...)` trả `xml(java[ok])`; `audit.record(...)` đánh số lần ghi đầu tiên bằng 1 rồi trả chuỗi như trên.

**`audit-count=1`:** chỉ có một lần `record`. `AuditSink` được đăng ký như singleton mặc định và service sử dụng đúng instance được container tạo.

**`same-audit=true`:** lookup theo type và lookup theo name trong cùng context trả **cùng bean singleton** `auditSink`.

Bài thực hành chứng minh tính độc lập giữa **cách khai báo bean** và **cách consumer nhận dependency**: constructor `AggregationService` không biết bean của mình đến từ XML, Java config hay API registration.

## 10. Lý giải trình tự container refresh khi dùng nhiều nguồn

Trong demo:

```text
1. new AnnotationConfigApplicationContext()
   |
2. register(AppConfiguration.class)
   |
3. registerBean("auditSink", AuditSink.class, AuditSink::new)
   |
4. refresh()
   |
   +--> ConfigurationClassPostProcessor:
   |      @Import FormattingConfiguration
   |      @ImportResource legacy-beans.xml
   |      @ComponentScan demo.scanned
   |
   +--> các bean definitions trở nên khả dụng trong registry
   +--> BeanFactoryPostProcessor / BeanPostProcessor tương ứng
   +--> tạo non-lazy singletons & resolve dependencies
   |
5. getBean(AggregationService.class)
   |
6. close()
```

Đây là **mô hình logic**, không phải cam kết về thứ tự nội bộ từng bước được phát biểu chính xác cho mọi extension point. Một số configuration processing đăng ký definitions trong pha BFPP, và container có thể có các processor đặc biệt hoạt động sớm. Phần thiết yếu là **các nguồn cần được nạp/đăng ký đúng lúc, trước khi consumer bean được tạo**, để BeanFactory nhìn thấy dependency candidates.

### 10.1. Đăng ký bean definitions không đồng nghĩa lập tức tạo instance

Gọi `registerBean` thường đăng ký metadata và supplier; gọi `register` nạp class metadata. Với context chưa `refresh()`, nhiều bean application chưa được tạo. Khi refresh chạy, Spring chuẩn bị processors, resolve dependencies và thường pre-instantiate non-lazy singleton beans.

Điều đó giải thích vì sao không cần sắp xếp các lệnh đăng ký theo thứ tự “formatter trước, prefixer sau, service cuối” trong mã bootstrap. Dependencies khai báo đúng sẽ được giải quyết bằng bean factory; tránh dùng side effect của thứ tự registration làm hợp đồng nghiệp vụ.

### 10.2. Vì sao tên bean/kiểu trả về quan trọng?

Bean definition có thông tin tên/type/factory method. Khi `AggregationService` yêu cầu `Formatter`, Spring cần tìm được candidate type phù hợp. Với `@Bean`, return type nên được khai báo đủ cụ thể cho những injection point cần dùng.

Nếu `@Bean Object formatter() { return new Formatter("java"); }`, trước khi instance tồn tại Spring có thể chỉ dự đoán bean type là `Object` và việc injection theo `Formatter` có thể không được resolve ở giai đoạn cần thiết. Nên trả kiểu `Formatter` hoặc interface thích hợp mà consumer thực sự inject.

## 11. Phân biệt nhiều bean definitions với nhiều nguồn properties

Có hai kiểu “nguồn cấu hình” thường bị gọi cùng tên:

**A. Nguồn bean definitions:** XML `<bean>`, Java `@Bean`, component scanning, programmatic registration. Chúng quyết định **bean nào tồn tại** và cách tạo bean.

**B. Nguồn giá trị properties:** `application.properties`, environment variables, system properties, `@PropertySource`, `Environment`, secret stores, Spring Boot config data, v.v. Chúng chủ yếu cung cấp **giá trị cấu hình** cho bean.

Ví dụ:

```java
@Configuration(proxyBeanMethods = false)
@PropertySource("classpath:client.properties")
class ClientConfiguration {
    @Bean
    ClientOptions clientOptions(Environment environment) {
        return new ClientOptions(
                environment.getRequiredProperty("client.endpoint")
        );
    }
}
```

`ClientOptions` là class demo giả định, cần import `@PropertySource`, `@Bean`, `@Configuration`, `Environment`, và file `client.properties`. `@PropertySource` thêm resource vào `Environment`/property sources; nó **không tự tạo một bean `ClientOptions`**. Factory method `@Bean` mới là nguồn bean definition, còn property là dữ liệu để tạo object.

### 11.1. Một resource XML có thể cấu hình bean lẫn properties

XML có thể dùng `<context:property-placeholder location="..."/>` và `<bean>`. Đó là hai cơ chế phối hợp: property resolver giúp điền giá trị, còn bean element định nghĩa object. Trong migration, cần kiểm tra **thứ tự đăng ký post-processors** và những file properties thực sự được tìm trên classpath.

### 11.2. Spring Boot application.properties không đồng nghĩa @ImportResource

Spring Boot thường tự quản lý các nguồn cấu hình giá trị (`application.properties`/YAML, environment, command-line arguments, profiles...) theo quy tắc riêng. Điều đó không có nghĩa Boot tự đọc mọi file `legacy-beans.xml` và đăng ký toàn bộ bean trong đó. Muốn import legacy XML, vẫn cần `@ImportResource` hoặc cơ chế nạp XML tương ứng.

**Ghi nhớ:** “đã đọc file properties” khác “đã đăng ký một bean từ file XML”.

## 12. Dùng @ImportResource hay XmlBeanDefinitionReader?

### 12.1. @ImportResource: đơn giản khi Java config làm gốc

Với hầu hết ứng dụng Java-centric, chọn:

```java
@Configuration
@ImportResource({
    "classpath:legacy-beans.xml",
    "classpath:integration-beans.xml"
})
class AppConfiguration {}
```

Đây là cách declarative: khi configuration được xử lý, Spring nạp các XML resources đã liệt kê. Thích hợp khi tập XML cố định theo module/deployment.

### 12.2. XmlBeanDefinitionReader: linh hoạt khi bootstrap động

Khi muốn điều khiển trực tiếp nguồn XML, Spring cũng cung cấp `XmlBeanDefinitionReader`, ví dụ ý tưởng dùng cùng `GenericApplicationContext`:

```java
GenericApplicationContext context = new GenericApplicationContext();

XmlBeanDefinitionReader xml = new XmlBeanDefinitionReader(context);
xml.loadBeanDefinitions("classpath:legacy-beans.xml");

context.registerBean("auditSink", AuditSink.class, AuditSink::new);
context.refresh();
```

Snippet cần import `GenericApplicationContext`, `XmlBeanDefinitionReader`, `AuditSink`. Trong trường hợp này bạn tự điều phối XML/programmatic registration, nhưng nếu muốn dùng `@Configuration` và `@ComponentScan` trên cùng `GenericApplicationContext` còn cần **đăng ký annotation configuration infrastructure** thích hợp (ví dụ dùng `AnnotationConfigApplicationContext` hoặc `AnnotatedBeanDefinitionReader` và `ClassPathBeanDefinitionScanner`). Do đó không nên suy luận rằng cứ tạo một `GenericApplicationContext` là nó tự xử lý mọi annotation `@Configuration`.

### 12.3. Tiêu chí lựa chọn

| Nhu cầu | Cách ưu tiên |
| --- | --- |
| Ứng dụng chủ yếu dùng Java configuration, chỉ một ít XML | `@ImportResource` |
| Muốn quản lý rõ class cấu hình/module | `@Import` |
| Dự án legacy chủ yếu XML | `ClassPathXmlApplicationContext` hoặc XML-centric + annotation processors |
| Bootstrap chọn XML resources theo runtime | `XmlBeanDefinitionReader` với context/registry phù hợp |
| Chỉ cần test fixture thêm một object | `registerBean` hoặc test configuration `@Bean` |
| Nhiều service có stereotype trong package rõ ràng | `@ComponentScan` |

Đừng chọn cách phức tạp chỉ vì có nhiều nguồn; một composition root với `@Import`/`@ImportResource` và vài `@Bean` thường đủ.

## 13. Tránh tạo context khác chỉ để lấy dependency

Một anti-pattern:

```java
public ReportService reportService() {
    ClassPathXmlApplicationContext legacy =
            new ClassPathXmlApplicationContext("legacy-beans.xml");
    LegacyPrefixer prefixer = legacy.getBean(LegacyPrefixer.class);
    return new ReportService(prefixer);
}
```

Đoạn này là ví dụ phản diện, với `ReportService` giả định và cần imports. Nó dựng một XML context khác trong lúc tạo service:

- Bean singleton của XML context **không** dùng chung cache với context Java.
- Phải quản lý/đóng cả hai context đúng cách.
- Dependency resolution xuyên context không mặc nhiên có.
- Dễ rò rỉ tài nguyên, trùng pool, trùng lifecycle và khó test.
- Có thể vô tình tạo nhiều instance cùng type mà người viết tưởng chỉ có một.

Giải pháp đúng khi cần **một context**:

```java
@Configuration
@ImportResource("classpath:legacy-beans.xml")
class RootConfiguration {
    @Bean
    ReportService reportService(LegacyPrefixer prefixer) {
        return new ReportService(prefixer);
    }
}
```

`ReportService` vẫn là kiểu giả định. Bean XML được nạp cùng context và cung cấp qua parameter injection. Không cần tự tạo XML context.

## 14. Một context vẫn có thể xuất hiện nhiều bean cùng type

Việc ghép nhiều nguồn làm tăng khả năng định nghĩa trùng. Hai vấn đề phải phân biệt:

### 14.1. Trùng tên bean

Ví dụ XML đã có:

```xml
<bean id="formatter" class="demo.javaformat.Formatter">
    <constructor-arg value="xml"/>
</bean>
```

trong khi Java configuration cũng khai báo `@Bean Formatter formatter()`. Hai registrations dùng **cùng tên** `formatter`. Hành vi phụ thuộc quy tắc overriding và thứ tự đăng ký:

- Có môi trường từ chối override bean definition và ném lỗi.
- Có môi trường cho phép ghi đè definition theo các quy tắc cụ thể.
- Spring Boot thường chặt hơn với duplicate definitions theo mặc định so với một số trường hợp Spring Framework thuần.

**Không nên dùng “ai đăng ký sau sẽ thắng” như hợp đồng nghiệp vụ.** Chọn một owner cho mỗi bean name hoặc thiết kế tên khác nhau có chủ ý.

### 14.2. Nhiều bean cùng kiểu

XML khai báo `legacyFormatter` và Java `@Bean` khai báo `formatter`, cả hai return/bean class đều thuộc kiểu `Formatter`. Bây giờ injection point nhận một `Formatter` có thể gặp nhiều ứng viên.

Có các cách hợp lệ:

- `@Qualifier("formatter")` ở injection point nếu muốn bean Java.
- `@Primary` trên bean mặc định khi thực sự có một lựa chọn ưu tiên.
- Tên/qualifier chuyên biệt theo chức năng (ví dụ `publicJsonMapper` và `internalJsonMapper`).
- Inject `List<Formatter>` hoặc `Map<String, Formatter>` khi nghiệp vụ chủ động cần nhiều implementations.
- Chỉ nạp đúng module/bean set cần thiết vào context.

Không đổi mọi bean thành `@Primary` hoặc mở bean overriding tùy tiện để lờ đi xung đột.

## 15. Component scan có thể vô tình nạp lại Java configuration

`@Configuration` cũng mang semantics stereotype của `@Component`. Do đó nếu `@ComponentScan` quét package chứa những configurations khác, Spring có thể tự phát hiện và xử lý chúng. Khi đã `@Import` một configuration và cùng class cũng bị scan, Spring thường có cơ chế quản lý metadata cùng configuration class; nhưng việc quét rộng **vẫn có thể nạp những cấu hình/module không dự định bật**.

Trong demo, `@ComponentScan(basePackageClasses=AggregationService.class)` chỉ scan `demo.scanned` thay vì toàn bộ `demo`; nhờ vậy tránh cuốn `demo.config` vào vùng quét không cần thiết.

Một cấu trúc tốt:

```text
demo.config   -> Java configurations / composition root
demo.scanned  -> application components của module này
demo.legacy   -> XML-managed classes
demo.audit    -> programmatically registered classes
```

Package separation ở đây làm rõ intent, nhưng **không tự tạo isolation**: khi bean definitions được nạp, chúng vẫn thuộc cùng context.

## 16. Annotation processing có còn hoạt động với bean XML/programmatic?

**Có, nếu context đăng ký hạ tầng annotation tương ứng.** Ví dụ `AnnotationConfigApplicationContext` đã chuẩn bị các annotation configuration processors cốt lõi, để Spring có thể xử lý `@Autowired`, các callback/hạ tầng thích hợp trên managed beans dù definition đến từ XML hay `registerBean`.

Một class XML có field `@Autowired` vẫn có thể được inject khi instance do Spring tạo và `AutowiredAnnotationBeanPostProcessor` phù hợp đang hoạt động. **Component scanning không phải điều kiện duy nhất của annotation processing**.

### Cần phân biệt với XML-centric context

Nếu dùng `ClassPathXmlApplicationContext` với XML thuần và muốn xử lý `@Autowired`, thường dùng `<context:annotation-config/>`, hoặc `<context:component-scan/>` vốn bao hàm annotation processors thông dụng. Chỉ đọc `<bean>` definitions từ XML **không đồng nghĩa** mọi annotation đã tự được bật trong tất cả BeanFactory variants.

### Và không phải annotation nào cũng tự hoạt động

`@Transactional` cần transaction management; `@Scheduled` cần scheduling infrastructure; `@RequestMapping` cần web mapping. Chỉ vì bean được kết hợp vào context không có nghĩa mỗi annotation của mọi stack tự có behavior.

## 17. Tên bean, scope, lifecycle và dependency xuyên nguồn

### 17.1. Cùng một singleton mặc định trong cùng context

Nếu bean `AuditSink` được đăng ký programmatically một lần với singleton mặc định, XML-managed bean và Java-managed bean khác khi inject `AuditSink` cùng sẽ nhận bean được container resolve. Trong demo, `same-audit=true` minh họa identity trong cùng context.

Nếu tạo **hai** ApplicationContext khác nhau, mỗi context có thể có singleton của riêng nó; không trông chờ identity qua biên giới contexts.

### 17.2. Scope luôn quan trọng

Bean từ XML có thể khai báo `scope="prototype"`; bean `@Bean` có thể dùng `@Scope("prototype")`; programmatic registration có thể dùng BeanDefinitionCustomizer để cấu hình scope. Khi một singleton consumer inject prototype trực tiếp, Spring thường cấp instance prototype tại thời điểm tạo singleton; không tự lấy prototype mới trên mỗi method call. Cần `ObjectProvider<T>`/`Provider<T>` khi muốn deferred lookup có chủ đích.

### 17.3. Vòng đời và resources

Nếu XML bean/Java bean trả HTTP client hoặc connection pool, phải xác định:

- Ai sở hữu client/pool?
- Init callback có được container gọi không?
- Shutdown cần `destroy-method`, `@Bean(destroyMethod=...)`, `@PreDestroy` hay API đóng khác?
- Có vô tình tạo hai pools vì cùng nguồn được nạp hai lần hoặc trực tiếp gọi factory method không?

Tập trung resources vào bean definitions được quản lý và đóng **context sở hữu chúng** đúng thời điểm. Với resource externally owned, không để Spring đóng nhầm tài nguyên không thuộc quyền sở hữu.

### 17.4. Phụ thuộc vòng xuyên cấu hình

Một XML bean có constructor ref tới bean Java, trong khi bean Java lại constructor-inject XML bean có thể tạo vòng phụ thuộc. Việc đến từ hai nguồn khác nhau **không thay đổi quy tắc xử lý vòng** của Spring: constructor cycles thường không giải quyết được và cần thiết kế lại dependency direction.

## 18. Thêm PropertySource mà không tạo bean trùng

Một hệ thống đa nguồn thường có nhiều properties files. Ví dụ:

**`src/main/resources/service.properties`**

```properties
service.prefix=production
```

**Java config:**

```java
@Configuration(proxyBeanMethods = false)
@PropertySource("classpath:service.properties")
class ServiceSettingsConfiguration {
    @Bean
    String servicePrefix(Environment environment) {
        return environment.getRequiredProperty("service.prefix");
    }
}
```

Đây là snippet Java cấu trúc, cần imports tương ứng. `@PropertySource` thêm một nguồn **giá trị** vào `Environment`; `@Bean String servicePrefix(...)` mới tạo bean tên `servicePrefix`.

Trong ứng dụng thật, không nhất thiết tạo mọi chuỗi cấu hình thành bean `String` (có thể gây mơ hồ injection); thường gói properties thành settings object rõ kiểu hoặc dùng configuration binding nếu framework hỗ trợ.

### 18.1. Thứ tự ưu tiên properties không đồng nghĩa thứ tự override bean

Giả sử system environment và property file đều có key `service.prefix`. `Environment` có thứ tự ưu tiên giữa các `PropertySource`; điều đó **khác** việc có hai bean definitions cùng tên `servicePrefix`. Một bên chọn **giá trị cấu hình**, một bên quyết định **bean registration**, phải debug tách biệt.

### 18.2. @Value placeholders cần resolver tương ứng

Trong `AnnotationConfigApplicationContext`, cơ chế environment/property resolution được tích hợp, nhưng với một số container low-level hoặc cú pháp placeholder tùy chỉnh, phải có processor/resolver thích hợp. Không trông chờ `@PropertySource` tạo bean đúng chỉ vì file properties đã đọc thành công.

## 19. Những lỗi tích hợp thường gặp

### Lỗi 1 — Tạo hai ApplicationContext độc lập rồi tưởng chúng chung singleton cache

Hai context không tự chia sẻ bean definitions. Nếu muốn một context, dùng `@ImportResource`/`@Import` hoặc reader trên cùng registry. Nếu muốn parent-child hierarchy, cấu hình parent rõ ràng và hiểu dependency visibility.

### Lỗi 2 — Quên @ImportResource, XML resource không được nạp

Java context không tự phát hiện XML file chỉ vì nó có trong `src/main/resources`. Nạp nó qua `@ImportResource` hoặc `XmlBeanDefinitionReader` trong đúng bootstrap path.

### Lỗi 3 — Sai đường dẫn classpath

`@ImportResource("classpath:legacy-beans.xml")` yêu cầu resource ở classpath root của runtime. Nếu file đặt `src/main/resources/config/legacy-beans.xml` thì đường dẫn cần là `classpath:config/legacy-beans.xml`. Build/test runtime classpath phải có resource.

### Lỗi 4 — Component scan quá rộng

`@ComponentScan("demo")` có thể tìm cả modules/configurations không dự kiến. Sử dụng package marker nằm trong package mục tiêu, hoặc `@Import` explicit thay vì scan toàn bộ project.

### Lỗi 5 — Đăng ký programmatic bean sau khi refresh rồi chờ toàn bộ lifecycle khởi động lại

Nhiều bước refresh và post-processing đã chạy. Với bootstrap, đăng ký trước refresh; nếu cần dynamic registration thực sự, phải hiểu API/lifecycle và test riêng trường hợp đó.

### Lỗi 6 — Trùng bean name từ XML và @Bean

Một XML bean id `formatter` đụng method `@Bean formatter()`. Kết quả tùy chính sách overriding và thời điểm xử lý, có thể lỗi hoặc thay bean definition. Dùng owner duy nhất hoặc đổi tên có chủ đích.

### Lỗi 7 — Nhiều ứng viên cùng type gây injection ambiguity

Hai beans `Formatter` từ XML và Java nhưng constructor nhận một `Formatter`. Cần `@Qualifier`, `@Primary`, tập hợp hoặc tổ chức lại module. Đừng suy luận nguồn Java luôn được ưu tiên hơn XML.

### Lỗi 8 — Bean do XML khai báo có @Autowired nhưng processor chưa được bật

Ở XML-centric context không có `<context:annotation-config/>` hoặc infrastructure tương đương, field/method annotation có thể không hoạt động. Kiểm tra loại ApplicationContext và processors đã đăng ký, không thay thế bằng mở rộng component scan vô tội vạ.

### Lỗi 9 — Nạp XML cần namespace nhưng thiếu dependency

Một XML file có namespace cho transaction, AOP, JMS hoặc Spring Security có thể cần module/schema/namespace handler tương ứng trên classpath. XML trong demo chỉ dùng Spring beans namespace nên không cần những modules đó.

### Lỗi 10 — Trộn cấu hình XML và Java nhưng gọi new trên configuration

Tự `new ConfigurationClass()` hoặc gọi `@Bean` method từ object không managed có thể tạo object ngoài container. Đưa dependency qua constructor/factory method parameters; để BeanFactory quản lý source-derived instance.

### Lỗi 11 — Giấu type phía sau @Bean Object

Spring không luôn đoán được concrete type trước khi factory method chạy. Điều này có thể gây thiếu candidate hoặc khiến processor hạ tầng không được phát hiện sớm. Return type nên đủ rõ, đặc biệt `BeanFactoryPostProcessor`/`BeanPostProcessor`.

### Lỗi 12 — Biên dịch được Java nhưng XML constructor không khớp

Ví dụ XML `<constructor-arg value="abc"/>` trong khi class vendor không có constructor hợp lệ. Lỗi chỉ xuất hiện lúc đọc/tạo bean. Khi tích hợp XML, kiểm tra constructor signature, tên package và type conversion.

### Lỗi 13 — Nhầm @Import với @ImportResource

`@Import` nhận class/type config/selector/registrar. `@ImportResource` nhận địa chỉ tài nguyên XML (hoặc nguồn được reader phù hợp hỗ trợ). Đừng đặt `"classpath:legacy-beans.xml"` vào `@Import` hoặc truyền class configuration vào `@ImportResource`.

### Lỗi 14 — Suy ra thứ tự khởi tạo từ thứ tự import

Đừng mặc định XML đọc trước sẽ tạo singleton trước Java bean và ngược lại. Dependency graph, scope, lazy-init, BFPP/BPP và early bean creation ảnh hưởng thời điểm. Nếu thật sự cần ràng buộc thứ tự initialization, có thể dùng `@DependsOn` hoặc bean-definition depends-on nhưng tốt nhất là biểu diễn dependency thật.

### Lỗi 15 — Bean đã ở context nhưng stack chuyên biệt chưa hoạt động

Có controller bean không đồng nghĩa HTTP server/mapping đã khởi chạy; có repository bean không đồng nghĩa transaction được bật. Việc kết hợp bean definitions chỉ giải quyết **registry/DI/lifecycle cơ bản**, không tự dựng mọi infrastructure framework khác.

## 20. Kiểm tra một context đã ghép đủ nguồn

### 20.1. Kiểm tra theo bean name

```java
context.containsBean("formatter");
context.containsBean("legacyPrefixer");
context.containsBean("aggregationService");
context.containsBean("auditSink");
```

Đặt trong `main` sau `refresh()` như demo. Những lookup này giúp kiểm chứng **bean name hiện diện**. Không chứng minh dependency đã được inject đúng nếu không chạy hành vi thực tế.

### 20.2. Kiểm tra theo bean type

```java
Formatter formatter = context.getBean(Formatter.class);
LegacyPrefixer legacy = context.getBean(LegacyPrefixer.class);
AuditSink audit = context.getBean(AuditSink.class);
AggregationService service = context.getBean(AggregationService.class);
```

Đoạn này dùng các class và context của demo. Nếu một type có nhiều candidates, `getBean(Type.class)` có thể lỗi; khi đó dùng name + type hoặc qualifier tại injection point.

### 20.3. Xem bean definition và nguồn khai báo

```java
var factory = context.getBeanFactory();

var javaDefinition = factory.getBeanDefinition("formatter");
var xmlDefinition = factory.getBeanDefinition("legacyPrefixer");
var scanDefinition = factory.getBeanDefinition("aggregationService");
var apiDefinition = factory.getBeanDefinition("auditSink");

System.out.println(javaDefinition.getFactoryMethodName());
System.out.println(xmlDefinition.getBeanClassName());
System.out.println(scanDefinition.getBeanClassName());
System.out.println(apiDefinition.getBeanClassName());
```

Đây là một snippet để quan sát, không đặt stdout cứng cho mọi môi trường. Bean definition có thể mô tả factory method hoặc class name; một số field có thể `null` tùy cơ chế đăng ký. Trong ví dụ này, `formatter` đến từ `@Bean` (factory method), XML bean chỉ định class, scanner biết class, programmatic registration biết class/supplier. `BeanDefinition.getResourceDescription()` có thể cung cấp nguồn tài nguyên để hỗ trợ điều tra.

### 20.4. Điều tra missing bean đúng hướng

Khi `UnsatisfiedDependencyException` xảy ra, kiểm tra:

1. Class/bean definition có thực sự được nạp từ **nguồn dự kiến**?
2. Resource XML tìm thấy chưa, format/schema có hợp lệ không?
3. Component scan có bao phủ đúng package? Có bị exclude không?
4. Programmatic registration có diễn ra trước refresh không?
5. Bean candidates có bị `@Profile`/`@Conditional` làm loại không?
6. Có nhiều candidate cùng kiểu hoặc trùng bean name không?
7. Bean construction có lỗi thực tế vì dependency khác, không phải vì scanner thiếu không?

Cách tiếp cận này nhanh hơn việc thêm tràn lan `@ComponentScan` hoặc `@Autowired` vào các class bất kỳ.

## 21. Kiến trúc kết hợp nhiều nguồn trong dự án thực tế

### 21.1. Giai đoạn migrate XML → Java

Một lộ trình an toàn:

1. Chọn **một context làm composition root** và xác định ai sở hữu bootstrap.
2. Giữ XML legacy trong một hoặc vài `@ImportResource` references.
3. Đưa các bean mới vào Java configuration, dùng `@Import` để chia nhóm.
4. Dùng component scan có phạm vi giới hạn cho code mới mang stereotype.
5. Tránh tạo context riêng cho một tài nguyên chỉ nhằm lấy bean.
6. Dùng integration tests để xác nhận tên bean, type, qualifier và behavior xuyên nguồn.
7. Migrate từng nhóm bean XML sang `@Bean` khi có lợi, nhưng không cần di chuyển tất cả cùng lúc.
8. Sau mỗi nhóm migrate, xóa definition cũ để tránh duplicate registrations.
9. Kiểm tra lifecycle và shutdown behavior của các tài nguyên dài hạn.

Đây là cách “strangler migration” ở cấp cấu hình: chuyển dần ownership bean mà vẫn duy trì khả năng chạy ứng dụng.

### 21.2. Chia theo module và vẫn chung context

```text
bootstrap/
    AppConfiguration.java
    App.java

pricing/
    PricingConfiguration.java
    DiscountService.java

legacy/
    legacy-beans.xml

integration/
    HttpClientConfiguration.java

scanned/
    EventHandler.java
```

Composition root quyết định **cấu hình nào cần nạp**, từng module sở hữu bean definitions của mình. Dù cấu hình đến từ source files khác nhau, chúng có thể góp vào **một** context khi deployment yêu cầu.

### 21.3. Có nên dùng nhiều ApplicationContext?

**Có**, khi bạn thật sự cần parent-child hierarchy, child web context, isolation hoặc lifecycle khác nhau. Nhưng đây là quyết định kiến trúc riêng, không phải điều kiện để sử dụng XML và Java đồng thời.

Trong hierarchy điển hình, **child** có thể tìm bean ở **parent**, còn parent không nhìn thấy bean chỉ tồn tại trong child. Bean name trùng ở child có thể che bean cùng tên trong parent theo lookup rules. Scope/lifecycle cũng không được tự gộp thành một bean factory thống nhất. Do đó, nếu yêu cầu là “nhiều nguồn trong **một** context”, hãy ưu tiên ghép vào một registry thay vì mở thêm containers.

## 22. Bài tập thực hành

### Bài 1 — Chuyển bean XML sang Java @Bean

Trong demo, thử chuyển `LegacyPrefixer` từ XML sang:

```java
@Bean
LegacyPrefixer legacyPrefixer() {
    return new LegacyPrefixer("java-config");
}
```

Đưa method vào Java configuration và **xóa `@ImportResource` hoặc definition XML cũ** trước khi chạy để tránh hai nguồn cùng khai báo bean name `legacyPrefixer`. Dự đoán output sau thay đổi: phần ở giữa chuỗi sẽ là `java-config(java[ok])` thay vì `xml(java[ok])`. Không cần thay constructor `AggregationService`.

### Bài 2 — Thêm XML bean cùng kiểu Formatter

Thêm một bean XML `Formatter` tên `legacyFormatter`. Sau đó, dự đoán kết quả autowire constructor `AggregationService(Formatter ...)`.

Đáp án: có thể xuất hiện **nhiều candidates** `Formatter`; để giữ ý định rõ ràng, dùng `@Qualifier("formatter")` hoặc phân biệt chúng bằng marker/primary theo use case. Không mặc định Java bean có ưu tiên chỉ vì nó không đến từ XML.

### Bài 3 — Đổi programmatic bean thành @Bean

Chuyển `AuditSink` sang Java config:

```java
@Bean
AuditSink auditSink() {
    return new AuditSink();
}
```

Sau khi bỏ dòng `context.registerBean("auditSink", ...)` ở bootstrap, chương trình vẫn có thể in những giá trị giống trước nếu các dependencies khác giữ nguyên. Điều đó chứng minh consumer không cần biết registration source.

### Bài 4 — Bỏ component scan, dùng registerBean(Class)

Thay cơ chế scan cho `AggregationService` bằng:

```java
context.registerBean(AggregationService.class);
```

Đây là lời gọi để đặt **trước `refresh()`**, sau khi bỏ `@ComponentScan` tương ứng. Với class có một public constructor và đủ dependency beans, Spring có thể tạo service bằng constructor injection. Tên bean được generator tạo có thể khác nếu dùng API/metadata khác; test nên tìm theo type, hoặc truyền tên explicit `"aggregationService"`.

### Bài 5 — Phân biệt property source và bean source

Tạo `settings.properties` có `app.prefix=config-value`, rồi dùng `@PropertySource` để cung cấp giá trị cho một factory method `@Bean`. Kiểm tra rằng:

- Property được đọc qua `Environment`.
- Property không tự tạo bean của class bạn mong muốn.
- Bean mới chỉ xuất hiện khi đã có registration source (`@Bean`, XML, scan hoặc API).

## 23. Câu hỏi ôn tập và đáp án

**Câu 1.** Một `AnnotationConfigApplicationContext` có thể chứa đồng thời bean từ XML, Java `@Bean` và component scan không?

**Đáp:** Có. Có thể dùng `@ImportResource` để import XML, `@Import`/`register` cho Java configuration và `@ComponentScan` để tìm components, tất cả trong một context.

**Câu 2.** `@ImportResource` có tạo thêm một `ClassPathXmlApplicationContext` độc lập không?

**Đáp:** Không. Nó nhập bean definitions từ XML vào context Java đang được cấu hình.

**Câu 3.** `@ComponentScan` có bắt buộc để một bean XML được inject vào scanned component không?

**Đáp:** Scan cần thiết nếu bạn chọn cách tìm consumer component qua scanner; bean XML vẫn được đăng ký độc lập. Khi consumer đã ở context, injection theo type hoạt động trên tập bean candidates chung.

**Câu 4.** Vì sao phải hoàn tất `registerBean` trước `refresh()` trong bootstrap?

**Đáp:** Để processor và các bean cần khởi tạo trong refresh nhìn thấy registration ngay từ đầu, tránh thêm bean sau khi startup lifecycle đã chạy.

**Câu 5.** Có hai bean cùng type `Formatter` nhưng tên khác nhau. Điều gì có thể xảy ra khi inject một `Formatter`?

**Đáp:** Nếu không có cách xác định ứng viên duy nhất (qualifier/primary/tên phù hợp), Spring có thể báo `NoUniqueBeanDefinitionException`.

**Câu 6.** File `application.properties` tự khai báo một Spring bean `Formatter` không?

**Đáp:** Không. Nó thường cung cấp giá trị cấu hình, không phải bean definition; cần factory/configuration/registration tương ứng để có object bean.

**Câu 7.** Tạo hai ApplicationContext cùng nạp một XML file có dùng chung singleton của file đó không?

**Đáp:** Không tự động. Mỗi context có bean factory và singleton registry riêng.

**Câu 8.** Trong demo, bốn bean đến từ nguồn nào?

**Đáp:** `Formatter` từ Java `@Bean`, `LegacyPrefixer` từ XML, `AggregationService` từ component scan, `AuditSink` từ programmatic registration.

**Câu 9.** Spring phân biệt constructor injection dựa trên nguồn bean definition hay dựa trên candidates phù hợp?

**Đáp:** Dựa trên dependency metadata, type, qualifier, scope và các quy tắc resolution; consumer thường không cần biết bean nguồn đến từ XML hay Java.

**Câu 10.** Khi chuyển bean XML sang Java, điều quan trọng trước tiên là gì?

**Đáp:** Bảo đảm có **một registration owner rõ ràng**, tránh cùng tên/type bị đăng ký trùng không chủ ý, kiểm tra behavior, scope và lifecycle sau migration.

## 24. Checklist triển khai

Trước khi kết hợp nhiều nguồn cấu hình, xác nhận:

1. **Chỉ có một ApplicationContext nếu mục tiêu là dùng chung registry.**
2. Mọi nguồn được nạp/đăng ký đúng cách: `@Import`, `@ImportResource`, `@ComponentScan`, `registerBean`/`register`.
3. XML resources tồn tại đúng classpath và namespaces được thư viện hỗ trợ.
4. Component scanning giới hạn package/filter và không kéo vào modules ngoài dự kiến.
5. Java `@Bean` methods trả kiểu rõ ràng; dependency thể hiện bằng factory method parameters khi phù hợp.
6. Programmatic registrations hoàn tất trước `refresh()` trong bootstrap.
7. Bean names không va chạm ngoài ý muốn; injection types có lựa chọn ứng viên duy nhất hoặc qualifier rõ ràng.
8. Scope, lazy-init, callbacks và resource ownership được kiểm tra ở container chung.
9. Annotation processors/stack-specific infrastructure được bật khi cần, không suy luận từ việc có bean.
10. Test cùng context: đủ bean theo name/type, hành vi xuyên nguồn chạy thật, resources được đóng đúng.
11. Nếu dùng parent-child contexts, lập sơ đồ visibility/lifecycle riêng; không coi đó là một context.
12. Nguồn properties và nguồn bean definitions được phân biệt trong tài liệu và log chẩn đoán.

## 25. Tài liệu tham khảo chính thức

- [Spring Framework 6.2 — Composing Java-based Configurations](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/composing-configuration-classes.html): `@Import`, bean dependency xuyên configuration và `@ImportResource`.
- [Spring Framework 6.2 — Instantiating the Spring Container with AnnotationConfigApplicationContext](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/instantiating-container.html): `register`, `scan`, `refresh`.
- [Spring Framework 6.2 — Classpath Scanning and Managed Components](https://docs.spring.io/spring-framework/reference/6.2/core/beans/classpath-scanning.html): stereotype, base packages, filter.
- [Spring Framework 6.2 — Annotation-based Container Configuration](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config.html): annotation processors khi trộn với XML.
- [Spring Framework 6.2 — Bean Definitions and XML Imports](https://docs.spring.io/spring-framework/reference/6.2/core/beans/basics.html): XML `<bean>`, `<import/>` và bean metadata.
- [Spring Framework 6.2 — GenericApplicationContext Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/GenericApplicationContext.html): các overload `registerBean`.
- [Spring Framework 6.2 — ImportResource Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/annotation/ImportResource.html): import bean definitions từ resources.
- [Spring Framework 6.2 — PropertySource Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/annotation/PropertySource.html): phân biệt property metadata và bean registration.

**Ghi nhớ:** Java configuration, XML, component scan và programmatic API đều có thể đóng góp bean definitions vào **một Spring ApplicationContext**. Container giải quyết dependency giữa chúng bằng cùng cơ chế IoC, scope và lifecycle. **Một context có nhiều nguồn không giống nhiều contexts**, còn nguồn giá trị `@PropertySource` không phải nguồn bean definitions. Nắm rõ registration ownership và kiểm tra trùng tên/type là chìa khóa để kết hợp an toàn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="configuration-composition-004">Nhập cấu hình XML bằng @ImportResource</a>

<details>
<summary>Xem chi tiết</summary>

# Nhập cấu hình XML bằng @ImportResource trong Spring Core Container

## Mục tiêu

Spring Framework cho phép ứng dụng khai báo bean bằng **Java configuration** (`@Configuration` và `@Bean`), **component scanning** (`@Component` và các stereotype) hoặc **XML configuration** (`<beans>`, `<bean>`, `<property>`, `<constructor-arg>`). Trong một dự án lâu năm, việc chuyển toàn bộ cấu hình XML sang Java trong một lần có thể không cần thiết hoặc quá rủi ro. Một phần cấu hình cũ vẫn có giá trị, nhất là khi liên quan tới XML namespaces hoặc các integration đã hoạt động ổn định.

Annotation **`@ImportResource`** trong Spring Core Container giải quyết bài toán **dùng cấu hình Java làm điểm vào chính nhưng nhập thêm bean definitions từ tài nguyên XML**:

```java
@Configuration
@ImportResource("classpath:legacy-beans.xml")
public class AppConfig {
    // Có thể khai báo thêm các @Bean methods ở đây.
}
```

Sau khi Spring nạp `AppConfig` vào `AnnotationConfigApplicationContext`, các bean definitions trong `legacy-beans.xml` được đưa vào **cùng BeanFactory/ApplicationContext** với các bean khai báo bằng `@Bean`. Vì cùng container, chúng có thể tham chiếu và được tiêm qua lại theo cơ chế dependency injection của Spring.

Sau bài này bạn sẽ:

1. Hiểu `@ImportResource` làm gì, hoạt động ở giai đoạn nào và khác `@Import` ra sao.
2. Khai báo đúng đường dẫn `classpath:`, `file:` và nhiều tài nguyên XML.
3. Viết XML có `<beans>`, `<bean>`, `<property>`, `<constructor-arg>` và `<alias>` hợp lệ.
4. Tiêm một bean từ XML vào tham số `@Bean` của Java và tham chiếu **ngược lại** từ XML tới Java bean.
5. Dùng XML namespace `context:property-placeholder` với Java `@Value` khi cần.
6. Phân biệt tên bean, alias, số lượng bean độc lập, scope và lifecycle khi kết hợp hai nguồn cấu hình.
7. Chẩn đoán lỗi không tìm thấy XML, sai namespace/schema, thiếu bean hoặc đăng ký trùng.
8. Chuyển dần từ XML sang Java configuration mà vẫn giữ hợp đồng bean names và kiểm thử tương thích.

**Tư tưởng chính:** `@ImportResource` **nhập bean definitions**, không sao chép object, không tạo một container thứ hai và không tự chuyển đổi cú pháp XML thành phương thức `@Bean` trong source code.

## 1. Bối cảnh: tại sao cần nhập XML từ cấu hình Java?

Giả sử hệ thống đã có tài nguyên:

```xml
<!-- src/main/resources/legacy-beans.xml -->
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
          http://www.springframework.org/schema/beans
          https://www.springframework.org/schema/beans/spring-beans.xsd">
    <bean id="legacyClient" class="com.example.legacy.LegacyClient"/>
</beans>
```

Đây là ví dụ **minh họa**: `com.example.legacy.LegacyClient` phải thực sự tồn tại trên classpath để sử dụng.

Đồng thời ứng dụng mới chủ yếu dùng:

```java
@Configuration(proxyBeanMethods = false)
class BillingConfig {
    @Bean
    BillingService billingService(LegacyClient legacyClient) {
        return new BillingService(legacyClient);
    }
}
```

Nếu chỉ đăng ký `BillingConfig` mà chưa nạp XML, Spring không biết bean `legacyClient` nằm ở đâu. Có thể giải quyết bằng cách nhập XML:

```java
@Configuration(proxyBeanMethods = false)
@ImportResource("classpath:legacy-beans.xml")
class BillingConfig {
    @Bean
    BillingService billingService(LegacyClient legacyClient) {
        return new BillingService(legacyClient);
    }
}
```

Ở đây `@ImportResource` giữ nguyên cấu hình XML cũ trong khi service mới tiếp tục dùng **dependency injection qua tham số phương thức `@Bean`**. Không cần tạo `LegacyClient` thêm một lần trong Java chỉ vì nó đến từ XML.

### Một BeanFactory, nhiều nguồn metadata

```text
             AnnotationConfigApplicationContext
                          |
                Đăng ký AppConfig
                          |
           +--------------+--------------+
           |                             |
    @Bean methods Java          @ImportResource(...)
           |                             |
    BeanDefinition A              Đọc legacy-beans.xml
    BeanDefinition B                       |
           |                        BeanDefinition C
           |                        BeanDefinition D
           +--------------+--------------+
                          |
                        BeanFactory
                          |
             Dependency resolution
               theo type / name / qualifier
                          |
                    Managed beans
```

Mỗi bean definition sau cùng được Spring xử lý theo scope, lifecycle, autowiring, post-processors, factory methods, metadata và các điều kiện liên quan. **Nguồn khai báo** là Java hay XML không tự quyết định bean đó có được singleton, inject, proxy hay cleanup như bean bình thường hay không.

## 2. @ImportResource nằm ở đâu và có những thuộc tính nào?

Annotation `@ImportResource` thuộc:

```java
import org.springframework.context.annotation.ImportResource;
```

Đây là annotation cấp **TYPE** — thường đặt trên class `@Configuration`:

```java
@Configuration
@ImportResource("classpath:legacy-beans.xml")
public class AppConfig {
}
```

Ý nghĩa chính của các thuộc tính:

| Thuộc tính | Mục đích | Ví dụ |
| --- | --- | --- |
| `value` | Danh sách resource locations cần nhập; là alias với `locations` | `@ImportResource("classpath:legacy.xml")` |
| `locations` | Tên tường minh của danh sách resource locations | `@ImportResource(locations = {"classpath:a.xml", "classpath:b.xml"})` |
| `reader` | Chọn implementation `BeanDefinitionReader` chuyên dụng khi cần | `@ImportResource(reader = XmlBeanDefinitionReader.class, locations = "classpath:legacy.xml")` |

Trong trường hợp XML thông thường, **không phải chỉ định `reader`**: Spring sử dụng `XmlBeanDefinitionReader` mặc định cho XML. Theo API 6.2, tài nguyên có đuôi `.groovy` được xử lý bằng reader phù hợp cho Groovy khi có hạ tầng tương ứng; đó là khả năng của annotation, không có nghĩa mọi ứng dụng Spring mặc định cần cấu hình Groovy.

`value` và `locations` là **hai tên thay thế của cùng danh sách tài nguyên**, không phải hai nguồn nhập độc lập cần cộng lại. Hãy chọn **một cách viết** để tránh khó đọc.

### Import một file XML

```java
@ImportResource("classpath:legacy-beans.xml")
```

### Import nhiều file XML

```java
@ImportResource(locations = {
    "classpath:legacy/repositories.xml",
    "classpath:legacy/integrations.xml"
})
```

Mỗi file được đọc và đóng góp bean definitions vào cùng container. **Không tạo hai contexts** chỉ vì có hai file.

### Có cần @Configuration không?

Annotation được thiết kế chủ yếu để kết hợp cùng một class cấu hình được Spring xử lý. Cách dễ đọc, dễ kiểm chứng nhất là:

```java
@Configuration(proxyBeanMethods = false)
@ImportResource("classpath:legacy-beans.xml")
class RootConfig {
}
```

Đăng ký `RootConfig.class` vào `AnnotationConfigApplicationContext` hoặc import từ một configuration class khác. Việc tự viết `new RootConfig()` trong một Java method **không kích hoạt** Spring import XML; annotation phải được Spring configuration processing nhận diện.

## 3. @ImportResource khác @Import, <import/> và ClassPathXmlApplicationContext

Dù tên đều có chữ *import*, phạm vi rất khác nhau.

| Cơ chế | Nhận đối số gì? | Vai trò |
| --- | --- | --- |
| `@Import(OtherConfig.class)` | Class/configuration/component/selector theo hợp đồng annotation | Nhập cấu hình Java hoặc thành phần Spring liên quan |
| `@ImportResource("classpath:legacy.xml")` | Resource location string | Nhập bean definitions từ XML hoặc định dạng được reader hỗ trợ |
| XML `<import resource="other.xml"/>` | Đường dẫn tài nguyên nằm trong XML | XML nhập thêm một file cấu hình XML khác |
| `ClassPathXmlApplicationContext("legacy.xml")` | Tên resource XML | Tạo context theo lối XML-centric |
| `@ComponentScan(basePackages = "...")` | Tên package/class marker | Quét các class mang stereotype |

Ví dụ kết hợp Java config khác và XML:

```java
@Configuration(proxyBeanMethods = false)
@Import(RepositoryJavaConfig.class)
@ImportResource("classpath:legacy-integration.xml")
class ApplicationConfig {
}
```

Đây là **đoạn minh họa**; `RepositoryJavaConfig` phải là class hợp lệ trong dự án. `@Import` không thay `@ImportResource` khi đối số là file XML. Ngược lại, `@ImportResource` không có nhiệm vụ quét package Java để tìm các `@Service`.

### Hai hướng kết hợp Java và XML

Spring hỗ trợ cả hai cách tổ chức:

- **Java-centric:** Khởi động bằng `AnnotationConfigApplicationContext(AppConfig.class)` rồi `@ImportResource` nạp các file XML cần giữ lại.
- **XML-centric:** Khởi động bằng `ClassPathXmlApplicationContext`, rồi XML khai báo/import class `@Configuration` thích hợp và bật infrastructure annotation processing khi cần.

Bài này tập trung **Java-centric** đúng yêu cầu “Nhập cấu hình XML bằng `@ImportResource`”. Không cần tự tạo cả hai loại context cho một ứng dụng đơn giản rồi chuyển object qua lại.

## 4. Đường dẫn tài nguyên: classpath:, file: và nhiều files

Vì `@ImportResource` yêu cầu **resource locations**, đường dẫn phải khớp nơi XML thật sự được đóng gói/chạy.

### 4.1. classpath: là lựa chọn thông dụng

Với Maven layout:

```text
my-app/
├── pom.xml
└── src/
    └── main/
        ├── java/demo/AppConfig.java
        └── resources/
            ├── legacy-beans.xml
            └── legacy/
                └── adapters.xml
```

Maven đưa tài nguyên trong `src/main/resources` vào runtime classpath. Vì vậy:

```java
@ImportResource("classpath:legacy-beans.xml")
```

tìm file ở gốc classpath, còn:

```java
@ImportResource("classpath:legacy/adapters.xml")
```

tìm file ở thư mục `legacy` bên trong classpath.

**Sai phổ biến:** Viết `@ImportResource("src/main/resources/legacy-beans.xml")` rồi nghĩ đây là đường dẫn luôn đúng khi đóng gói JAR. Đường dẫn source tree không phải resource location ổn định ở runtime.

`classpath:/legacy-beans.xml` cũng thường được dùng để nhấn mạnh gốc classpath. Khi app được đóng gói, Spring tìm trên classpath, không phụ thuộc IDE đang mở thư mục nào.

### 4.2. file: trỏ đến filesystem

```java
@ImportResource("file:/opt/myapp/config/legacy-beans.xml")
```

`file:` dùng đường dẫn ngoài JAR, hữu ích cho cấu hình do deployment quản lý. Cần bảo đảm:

- File tồn tại ở nơi **máy/container runtime** có thể đọc.
- Đường dẫn phù hợp hệ điều hành và volume mount.
- Quyền truy cập, versioning, integrity và cách rollout được kiểm soát.
- Đừng nhúng secrets hay dựa vào file ngoài mà không có chính sách bảo mật, cấu hình và lỗi rõ ràng.

Đoạn này là minh họa; file `/opt/myapp/config/legacy-beans.xml` không thuộc dự án runnable ở mục 7.

### 4.3. classpath*: và resource patterns

Spring có cơ chế `ResourcePatternResolver` hỗ trợ patterns như `classpath*:` trong một số ngữ cảnh. Khi dùng resource patterns cần hiểu số lượng match có thể khác giữa IDE, JAR và nhiều dependency có resource trùng tên.

Ví dụ dạng minh họa:

```java
@ImportResource("classpath*:META-INF/spring/legacy-context.xml")
```

Có thể nhận nhiều resource trên classpath phù hợp theo pattern resolver. **Đừng** áp dụng wildcard rộng chỉ để chắc chắn “sẽ tìm được XML”: import nhiều bản XML ngoài ý muốn có thể gây duplicate bean definitions hoặc tạo các component không được phép. Với một file rõ ràng, `classpath:legacy-beans.xml` thường đơn giản và an toàn hơn.

### 4.4. Resource không tìm thấy sẽ làm gì?

Nếu location không tồn tại/không đọc được, quá trình parse/register XML có thể ném exception và context không khởi động thành công. `@ImportResource` **không có nghĩa** XML là optional. Không giả định thiếu file sẽ bị bỏ qua như một dependency tùy chọn.

Đừng bắt mọi lỗi startup rồi tiếp tục ứng dụng trong trạng thái cấu hình thiếu; hãy xác định đúng file và cách đóng gói.

## 5. Cách khai báo XML cơ bản để nhập

Một XML Spring hiện đại thường bắt đầu bằng namespace và schema như sau:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd">

    <bean id="legacyFormatter"
          class="com.example.legacy.LegacyFormatter">
        <property name="prefix" value="LEGACY:"/>
    </bean>
</beans>
```

Đây là ví dụ **minh họa**: class `com.example.legacy.LegacyFormatter` cần có constructor/property setter tương ứng. Đối với bean do XML tạo:

- `id` thường là bean name chính.
- `class` là **fully qualified Java class name** mà classloader phải tìm được.
- `<property name="prefix" value="..."/>` thiết lập thuộc tính qua setter/JavaBean property phù hợp.
- `<constructor-arg>` truyền tham số constructor.
- `ref="otherBean"` tham chiếu bean definition có tên tương ứng trong **cùng container**, dù bean kia được khai báo bằng Java.
- XML có thể khai báo `scope`, `init-method`, `destroy-method` theo lifecycle mong muốn.

Một bean XML bình thường không cần đồng thời gắn `@Component` lên Java class; nếu đã được khai báo bằng `<bean>`, Spring có bean definition từ XML.

### Dùng constructor-arg thay property

```xml
<bean id="legacyProcessor"
      class="com.example.legacy.LegacyProcessor">
    <constructor-arg ref="legacyFormatter"/>
</bean>
```

Đây cũng là đoạn minh họa, yêu cầu `LegacyProcessor` có constructor phù hợp. Chọn constructor injection khi dependency bắt buộc; property injection hữu ích cho những thư viện JavaBean cũ có setter, nhưng không nên dùng nó chỉ vì XML trông ngắn hơn.

### Alias trong XML

```xml
<alias name="legacyFormatter" alias="oldFormatter"/>
```

Alias `oldFormatter` trỏ tới **cùng bean definition** với `legacyFormatter`. Nó **không tạo** một bean `LegacyFormatter` mới. Với singleton mặc định, gọi hai tên cho cùng reference. Đừng nhầm alias với hai thẻ `<bean>` có `id` khác nhau, vốn tạo hai definitions độc lập.

## 6. Bean XML và Java tương tác hai chiều như thế nào?

Khi import XML vào cùng một `AnnotationConfigApplicationContext`, dependency resolution không chia thành “vùng Java” và “vùng XML”.

### 6.1. Java @Bean nhận dependency do XML khai báo

Ví dụ XML:

```xml
<bean id="legacyGateway"
      class="com.example.gateway.LegacyGateway"/>
```

Còn Java config:

```java
@Configuration(proxyBeanMethods = false)
@ImportResource("classpath:legacy-gateway.xml")
class PaymentConfig {

    @Bean
    PaymentService paymentService(LegacyGateway legacyGateway) {
        return new PaymentService(legacyGateway);
    }
}
```

Đây là minh họa cần các class và file thực. Spring tìm bean `LegacyGateway` trong BeanFactory và truyền vào factory method `paymentService`. Không cần `new LegacyGateway()` trong Java config.

Nếu có nhiều bean `LegacyGateway` cùng type, dependency có thể nhập nhằng; chọn bằng `@Qualifier`/`@Primary` hoặc cấu trúc interface rõ. XML bean có thể dùng `primary="true"` hoặc `autowire-candidate` trong những tình huống tương ứng.

### 6.2. XML ref tới bean được khai báo trong Java

Java config:

```java
@Configuration(proxyBeanMethods = false)
@ImportResource("classpath:legacy-dashboard.xml")
class AppConfig {

    @Bean("reportService")
    ReportService reportService() {
        return new ReportService();
    }
}
```

XML:

```xml
<bean id="legacyDashboard"
      class="com.example.legacy.LegacyDashboard">
    <property name="reportService" ref="reportService"/>
</bean>
```

Spring sẽ phân giải `ref="reportService"` trong cùng BeanFactory. Điều kiện là bean `reportService` phải tồn tại và tương thích với property setter `setReportService(...)`. Cấu hình Java được import XML thông qua `@ImportResource`, nhưng bean definitions vẫn được xử lý chung trong quá trình dựng context; XML không bắt buộc phải xuất hiện sau bean Java trong source để được resolve.

**Nhớ:** `ref` tham chiếu **tên bean**, khác với `value` truyền chuỗi literal. Đừng viết `value="reportService"` nếu property mong một instance `ReportService`.

### 6.3. Khai báo dependency bằng type hay name?

- Trong Java, factory method parameter như `ReportService service` biểu diễn dependency theo **kiểu**, có thể kết hợp qualifier.
- Trong XML, `ref="reportService"` biểu diễn dependency theo **tên bean**.
- Cùng một bean có thể được lấy bằng cả type và name, nếu lựa chọn ứng viên không nhập nhằng.
- Alias trong XML có thể cung cấp một tên tương thích với code cũ mà vẫn cùng bean.

Đây là một lý do cần giữ **bean names ổn định** khi di trú cấu hình XML sang Java: consumer XML cũ thường tham chiếu tên tường minh.

## 7. Dự án runnable Java 17: XML ↔ Java trong một ApplicationContext

Ví dụ này có thể chạy với **JDK 17+, Maven và Spring Framework 6.2**. Nó không dùng database, mạng hay Spring Boot.

Chúng ta sẽ tạo:

1. Bean `xmlStore` và `formatter` bằng XML.
2. Alias XML `legacyStore` trỏ tới `xmlStore`.
3. Bean `messageService` bằng `@Bean`, nhận `MessageStore` và `PrefixFormatter` từ XML.
4. Bean `xmlDashboard` bằng XML, có property `messageService` tham chiếu ngược tới bean được tạo từ Java.
5. ApplicationContext chỉ được tạo **một lần** qua `AnnotationConfigApplicationContext`.

### 7.1. Cấu trúc thư mục

```text
import-resource-demo/
├── pom.xml
└── src/
    └── main/
        ├── java/
        │   └── demo/
        │       └── ImportResourceDemo.java
        └── resources/
            └── legacy-beans.xml
```

### 7.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="
           http://maven.apache.org/POM/4.0.0
           https://maven.apache.org/xsd/maven-4.0.0.xsd">

    <modelVersion>4.0.0</modelVersion>

    <groupId>demo</groupId>
    <artifactId>import-resource-demo</artifactId>
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

### 7.3. Mã Java đầy đủ

Tạo file `src/main/java/demo/ImportResourceDemo.java`:

```java
package demo;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;

public class ImportResourceDemo {

    public interface MessageStore {
        String persist(String formatted);
    }

    // Class công khai, static để Spring XML instantiation không
    // cần instance của outer class ImportResourceDemo.
    public static class XmlMessageStore implements MessageStore {

        public XmlMessageStore() {
        }

        @Override
        public String persist(String formatted) {
            return "stored[" + formatted + "]";
        }
    }

    public static class PrefixFormatter {
        private String prefix;

        public PrefixFormatter() {
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }

        public String format(String message) {
            return prefix + message;
        }
    }

    public static class MessageService {
        private final MessageStore store;
        private final PrefixFormatter formatter;

        public MessageService(MessageStore store,
                              PrefixFormatter formatter) {
            this.store = store;
            this.formatter = formatter;
        }

        public String submit(String message) {
            return store.persist(formatter.format(message));
        }
    }

    public static class XmlDashboard {
        private MessageService messageService;

        public XmlDashboard() {
        }

        public void setMessageService(MessageService service) {
            this.messageService = service;
        }

        public String status() {
            return "xml->java:" + messageService.submit("dashboard");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ImportResource("classpath:legacy-beans.xml")
    public static class AppConfig {

        @Bean
        public MessageService messageService(
                @Qualifier("xmlStore") MessageStore store,
                PrefixFormatter formatter) {
            return new MessageService(store, formatter);
        }
    }

    public static void main(String[] args) {
        try (var context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {

            System.out.println("xmlBean=" +
                    context.containsBean("xmlStore"));
            System.out.println("javaBean=" +
                    context.containsBean("messageService"));
            System.out.println("aliasSame=" +
                    (context.getBean("xmlStore") ==
                     context.getBean("legacyStore")));

            MessageService service =
                    context.getBean(MessageService.class);
            XmlDashboard dashboard =
                    context.getBean(XmlDashboard.class);

            System.out.println("service=" +
                    service.submit("order-42"));
            System.out.println("dashboard=" +
                    dashboard.status());
            System.out.println("storeBeanCount=" +
                    context.getBeanNamesForType(MessageStore.class).length);
        }
    }
}
```

Giải thích ngắn về Java file:

- `XmlMessageStore` và `PrefixFormatter` là **public static nested classes**. Trong Spring XML, tên binary class của chúng chứa ký tự `$`. Việc dùng `public static` tránh cần tạo outer instance khi XML instantiate object.
- `@ImportResource` đặt trên `AppConfig`. `AnnotationConfigApplicationContext` nạp class này, từ đó xử lý thêm XML.
- `messageService(...)` nhận hai dependency **do Spring cung cấp**; `@Qualifier("xmlStore")` thể hiện rõ bean XML cần lấy.
- `proxyBeanMethods=false` hoàn toàn phù hợp vì method `@Bean` không gọi trực tiếp một `@Bean` method khác.
- `XmlDashboard` có setter vì ví dụ chủ đích minh họa **property injection từ XML tới bean Java**; ở code nghiệp vụ mới, constructor injection thường tốt hơn nếu dependency bắt buộc.

### 7.4. XML khai báo hai bean và tham chiếu trở lại Java

Tạo file `src/main/resources/legacy-beans.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd">

    <!-- XML-defined bean, được Java @Bean inject theo type + qualifier. -->
    <bean id="xmlStore"
          class="demo.ImportResourceDemo$XmlMessageStore"/>

    <!-- Bí danh tương thích cho cùng một bean. -->
    <alias name="xmlStore" alias="legacyStore"/>

    <!-- Setter property injection vào bean được định nghĩa bằng XML. -->
    <bean id="formatter"
          class="demo.ImportResourceDemo$PrefixFormatter">
        <property name="prefix" value="XML:"/>
    </bean>

    <!-- XML-defined bean tham chiếu ngược Java @Bean messageService. -->
    <bean id="xmlDashboard"
          class="demo.ImportResourceDemo$XmlDashboard">
        <property name="messageService" ref="messageService"/>
    </bean>

</beans>
```

**Quan trọng khi copy code:**

- Tên XML class dùng `demo.ImportResourceDemo$XmlMessageStore` chứ không dùng `demo.ImportResourceDemo.XmlMessageStore`. Dấu `$` là cách JVM định danh nested class trong tên binary class.
- XML được lưu tại **`src/main/resources/legacy-beans.xml`**, tương ứng trực tiếp với `classpath:legacy-beans.xml`.
- `<property name="prefix" value="XML:"/>` truyền **chuỗi** cho setter `setPrefix`.
- `<property name="messageService" ref="messageService"/>` yêu cầu Spring tìm **bean Java có tên `messageService`**, không phải chuỗi “messageService”.
- `legacyStore` là alias của `xmlStore`. Hai tên đều được chấp nhận khi tra cứu bean singleton.

### 7.5. Lệnh chạy

Từ thư mục dự án chứa `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.ImportResourceDemo
```

Yêu cầu JDK 17+ và Maven. Những dòng **do chương trình tự in ra** (không tính thông điệp/log Maven hoặc Spring):

```text
xmlBean=true
javaBean=true
aliasSame=true
service=stored[XML:order-42]
dashboard=xml->java:stored[XML:dashboard]
storeBeanCount=1
```

### 7.6. Tại sao output đúng?

**`xmlBean=true`:** `xmlStore` được Spring đọc từ `legacy-beans.xml` khi xử lý `@ImportResource`. Nó là bean definition trong cùng context.

**`javaBean=true`:** `messageService` được đóng góp bởi Java `@Bean MessageService messageService(...)`; tên mặc định là tên method.

**`aliasSame=true`:** XML `<alias name="xmlStore" alias="legacyStore"/>` thêm một **tên khác** cho cùng singleton. Hai lời gọi `getBean` trả cùng reference.

**`service=stored[XML:order-42]`:** Java bean `MessageService` nhận `PrefixFormatter` có prefix `XML:` từ XML property injection. Service format `order-42` thành `XML:order-42` rồi gọi bean store, trả về `stored[XML:order-42]`.

**`dashboard=xml->java:stored[XML:dashboard]`:** Bean `xmlDashboard` được định nghĩa bằng XML, nhưng property `messageService` tham chiếu bean Java. Method `status()` gọi service được inject để tạo chuỗi có tiền tố `xml->java:`.

**`storeBeanCount=1`:** `getBeanNamesForType(MessageStore.class)` trả danh sách **bean definitions thực** theo type. Alias `legacyStore` không phải một `MessageStore` bean thứ hai. Không có một `new XmlMessageStore()` khác được khai báo trong Java.

### 7.7. Những thay đổi có thể tự thử

1. **Đổi tên alias** `legacyStore` thành `oldStore` trong XML, sửa lời gọi `getBean("oldStore")` rồi chạy. Tên chính `xmlStore` vẫn không đổi.
2. **Đổi prefix** từ `XML:` thành `LEGACY:` ở XML. Hai dòng `service` và `dashboard` đổi theo; không phải sửa Java factory method.
3. **Xóa `@ImportResource`** khỏi `AppConfig`. Context sẽ không tìm được các dependency XML cần thiết cho `messageService`, nên quá trình tạo bean sẽ thất bại, thay vì in output như trên.
4. **Đổi XML `ref="messageService"` thành `ref="unknownService"`**. Spring không có bean với tên đó và báo lỗi resolution. Đây là ví dụ vì sao bean names là hợp đồng giữa Java và XML.
5. **Thêm XML bean thứ hai cũng implement `MessageStore`**. Parameter `MessageStore` có `@Qualifier("xmlStore")` nên vẫn chọn bean phù hợp; nếu bỏ qualifier và không có ứng viên thắng, Spring có thể báo `NoUniqueBeanDefinitionException`.

## 8. Cấu hình placeholder từ XML và @Value trong Java

Một ứng dụng có thể giữ cấu hình property placeholder trong XML nhưng dùng giá trị ấy tại Java `@Configuration`. Đây là trường hợp Spring tài liệu chính thức nêu để minh họa `@ImportResource`.

Ví dụ một tài nguyên `properties-config.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:context="http://www.springframework.org/schema/context"
       xsi:schemaLocation="
         http://www.springframework.org/schema/beans
         https://www.springframework.org/schema/beans/spring-beans.xsd
         http://www.springframework.org/schema/context
         https://www.springframework.org/schema/context/spring-context.xsd">

    <context:property-placeholder
            location="classpath:application-legacy.properties"/>
</beans>
```

File `src/main/resources/application-legacy.properties`:

```properties
app.label=legacy-enabled
app.retryLimit=3
```

Java config:

```java
// Đoạn minh họa: CustomClient là một class ứng dụng.
@Configuration(proxyBeanMethods = false)
@ImportResource("classpath:properties-config.xml")
class PropertyConfig {

    @Bean
    CustomClient customClient(
            @Value("${app.label}") String label,
            @Value("${app.retryLimit}") int retryLimit) {
        return new CustomClient(label, retryLimit);
    }
}
```

Cần import `org.springframework.beans.factory.annotation.Value` và các annotation `Configuration`, `ImportResource`, `Bean`. Đây là **đoạn mở rộng minh họa**, không thuộc chương trình chạy độc lập ở mục 7. Sau khi import XML, `<context:property-placeholder>` đăng ký hạ tầng xử lý property placeholders phù hợp để `@Value` đọc giá trị.

**Điểm cần chú ý:**

- `${app.label}` là cú pháp placeholder của Spring, **không phải** cú pháp nội suy biến của Java.
- Spring đọc file từ runtime classpath. Nếu file missing, placeholder chưa giải quyết hoặc kiểu không chuyển được, cấu hình có thể lỗi tùy context/policy xử lý placeholder.
- `retryLimit` ở Java có kiểu `int`; Spring sẽ chuyển chuỗi property `3` thành số khi cơ chế conversion phù hợp.
- Nếu ứng dụng đã có `PropertySourcesPlaceholderConfigurer` hoặc một cơ chế property resolver khác, tránh vô tình đăng ký nhiều processors có chính sách xung đột.
- Trong Spring Framework thuần cũng có thể khai báo property source hoàn toàn bằng Java, không nhất thiết giữ XML nếu chỉ còn một property file đơn giản.

### @PropertySource có thay được @ImportResource không?

`@PropertySource("classpath:application.properties")` thêm property source vào Spring Environment; nó **không** tự đọc các `<bean>` definitions từ XML. Nếu mục tiêu duy nhất là đọc key-value, `@PropertySource`/`Environment` hoặc cấu hình Boot tương ứng có thể đủ. Nếu mục tiêu là giữ bean definitions hay XML namespaces, dùng `@ImportResource`.

## 9. Nạp XML namespace: khi nào Java-centric vẫn hợp lý?

Một số integration lâu năm hoặc thư viện third-party cung cấp XML namespaces để khai báo hạ tầng ngắn gọn, ví dụ `<context:property-placeholder>`. Spring có thể xử lý namespace handlers khi JAR chứa các metadata/schema handlers tương ứng được đưa lên classpath.

`@ImportResource` cho phép ứng dụng Java-centric chỉ nhập những tài nguyên XML này:

```java
@Configuration(proxyBeanMethods = false)
@ImportResource({
    "classpath:legacy/namespace-config.xml",
    "classpath:legacy/adapters.xml"
})
public class ApplicationConfig {
}
```

Đây là minh họa nhiều file. Không cần viết lại mọi XML namespace trước khi bắt đầu dùng Java configuration.

Tuy nhiên:

- XML namespace phải được khai báo đúng `xmlns` và `xsi:schemaLocation`.
- Namespace handler/schema tương ứng phải được cung cấp bởi dependencies hợp lệ trên runtime classpath.
- Một namespace không được hỗ trợ hoặc một version API đã bị loại bỏ có thể gây lỗi parse XML.
- Di trú nên giảm dần XML đã lỗi thời, không biến `@ImportResource` thành cơ chế che mã cấu hình mà nhóm không còn hiểu rõ.

Có thể giữ những phần XML đang ổn định lâu dài nếu phù hợp chi phí/bảo trì; không có yêu cầu rằng mọi Spring Framework project phải chuyển 100% sang annotations.

## 10. Bean names và aliases khi XML và @Bean cùng container

Bean names là hợp đồng đặc biệt quan trọng trong cấu hình lai.

### Java @Bean có tên mặc định

```java
@Bean
PaymentService paymentService(PaymentGateway gateway) {
    return new PaymentService(gateway);
}
```

Tên bean mặc định là `paymentService`. XML có thể tham chiếu:

```xml
<property name="paymentService" ref="paymentService"/>
```

Nếu đổi Java method `paymentService()` thành `createService()` mà không đặt tên tường minh, bean name thay đổi. XML cũ có thể bị hỏng dù type Java không đổi.

### Giữ bean name tường minh khi refactor

```java
@Bean("paymentService")
PaymentService createPaymentService(PaymentGateway gateway) {
    return new PaymentService(gateway);
}
```

Tên chính vẫn là `paymentService` trong khi tên factory method thay đổi. Đây là phương án hữu ích cho quá trình chuyển đổi cấu hình mà XML consumer còn tồn tại.

### Bean-name aliases ở hai định dạng

Java:

```java
@Bean(name = {"paymentService", "legacyPaymentService"})
PaymentService createPaymentService(PaymentGateway gateway) {
    return new PaymentService(gateway);
}
```

XML:

```xml
<alias name="paymentService" alias="oldPaymentService"/>
```

Hai ví dụ cho thấy nhiều tên có thể chỉ về một bean. Không nên tự động dùng **cả hai** mà không kiểm tra xung đột. Alias giải quyết tương thích tên; nó không phải phương tiện tạo hai implementations hoặc copy trạng thái của bean.

### Tránh trùng tên giữa XML và Java

Nếu XML đăng ký:

```xml
<bean id="paymentService" class="com.example.LegacyPaymentService"/>
```

và Java cũng đăng ký `@Bean("paymentService")`, hai definitions tranh cùng tên. Hành vi cụ thể tùy container và chính sách bean definition overriding; ứng dụng có thể lỗi hoặc một definition thay definition khác theo rules hiện hành. **Đừng dựa vào thứ tự đọc file để cố tình override** mà chưa cấu hình chính sách rõ và chưa test.

Với Spring Boot, mặc định overriding có thể bị từ chối; với plain Spring Framework, BeanFactory có các cờ điều khiển overriding. Hãy coi trùng tên không chủ đích là **lỗi thiết kế cần giải quyết**, thay vì mặc định là “Java luôn ghi đè XML”.

## 11. Scope, lazy bean và lifecycle trong cấu hình lai

Một bean được khai báo từ XML vẫn là bean bình thường của Spring:

```xml
<bean id="client"
      class="com.example.LegacyClient"
      scope="singleton"
      lazy-init="true"
      init-method="initialize"
      destroy-method="shutdown"/>
```

Các metadata trong ví dụ trên có ý nghĩa tương ứng:

- `scope="singleton"`: một managed instance trong phạm vi BeanFactory.
- `lazy-init="true"`: cố trì hoãn tạo singleton cho tới khi có nhu cầu; eager dependency có thể vẫn khiến nó được tạo sớm.
- `init-method="initialize"`: callback khởi tạo khi Spring tạo managed bean.
- `destroy-method="shutdown"`: callback cleanup cho bean phù hợp khi container hủy, thường là lúc close context đối với singleton.

Một bean tạo bằng Java `@Bean` cũng có thể dùng `@Scope`, `@Lazy`, `initMethod`/`destroyMethod`. Không có một bộ quy tắc lifecycle tách biệt chỉ vì bean đến từ XML.

### Nhận dependency XML qua parameter là an toàn

```java
@Configuration(proxyBeanMethods = false)
@ImportResource("classpath:legacy-beans.xml")
class ServiceConfig {

    @Bean
    ModernService modernService(LegacyClient client) {
        return new ModernService(client);
    }
}
```

Cách này để **BeanFactory** xử lý scope và lifecycle của `LegacyClient`. Không cần và không nên viết `new ClassPathXmlApplicationContext(...)` bên trong `@Bean modernService()` để lấy client, vì như vậy bạn tạo một context **thứ hai** với singleton/lifecycle riêng, dễ leak và gây khác bean identity.

### Prototype và bean được inject vào singleton

Nếu XML bean là `scope="prototype"` nhưng Java singleton service nhận nó qua constructor/factory parameter một lần, service thường giữ reference tới prototype đã được tạo tại thời điểm inject. Điều này không có nghĩa service sẽ tự được inject prototype mới mỗi request. Nếu cần lấy bean theo từng lượt, cân nhắc `ObjectProvider<LegacyClient>` hoặc factory hợp lý.

### Cleanup

Khi tạo `AnnotationConfigApplicationContext` bằng Java, hãy đóng context:

```java
try (var context =
        new AnnotationConfigApplicationContext(AppConfig.class)) {
    // Dùng các bean từ XML và Java.
}
```

Context đóng sẽ kích hoạt cleanup/lifecycle callbacks cho các managed singleton phù hợp. Với prototype, Spring không mặc định quản lý destruction giống singleton sau khi cấp object cho client.

## 12. XML không thể cứu một class Java chưa có trên classpath

`@ImportResource` đọc **metadata**, không tải bytecode của những class bị thiếu từ Internet và không tự thêm Maven dependency.

Nếu XML chứa:

```xml
<bean id="legacyAdapter"
      class="com.vendor.legacy.Adapter"/>
```

nhưng `com.vendor.legacy.Adapter` không có trong runtime dependencies, container có thể báo `CannotLoadBeanClassException` hoặc một bean creation/classloading exception tương ứng. Sửa đường dẫn `@ImportResource` không khắc phục được class missing.

Khi di trú multi-module:

1. Đảm bảo module/JAR có class implementation trên runtime classpath.
2. Đảm bảo XML nằm ở resource path đúng.
3. Đảm bảo XML schema/namespace dependencies tương thích phiên bản Spring hiện tại.
4. Đảm bảo bean names và kiểu property/constructor arguments phù hợp.
5. Test ApplicationContext tạo được cả Java và XML beans.

Đừng chỉ kiểm tra build compile thành công của Java code: XML bean class names là **chuỗi**, lỗi rename package/class đôi khi chỉ xuất hiện khi Spring parse/instantiate context.

## 13. So sánh với những phương án cấu hình khác

| Nhu cầu | Phương án thường phù hợp | Lý do |
| --- | --- | --- |
| Giữ XML cũ, Java là điểm bootstrap chính | `@ImportResource` | Chèn XML bean definitions vào cùng context |
| Import một Java configuration class khác | `@Import` | Type-safe class reference và composition |
| Chỉ đọc properties | `@PropertySource`, `Environment` hoặc cơ chế Boot | Không cần parser XML cho key-value thuần |
| Đăng ký class ứng dụng có stereotype | `@ComponentScan` | Quét package, không cần liệt kê XML bean |
| Đăng ký client third-party bằng builder | `@Bean` | Factory method Java rõ kiểu, tham số |
| XML làm điểm bootstrap chính | `ClassPathXmlApplicationContext` | Hợp với hệ thống XML-centric |
| Chia một file XML thành nhiều modules | XML `<import resource="..."/>` | Composition ngay trong DSL XML |
| Chọn bean cụ thể khi có nhiều type matches | `@Qualifier`/`@Primary` | Giải quyết ambiguity, không quyết định nguồn cấu hình |

Đây là các công cụ **bổ sung**, không phải thứ hạng tốt–xấu tuyệt đối. Một dự án mới có thể không cần XML; một dự án legacy vẫn có thể dùng `@ImportResource` như bước đệm ổn định.

## 14. Di trú từ XML sang Java theo từng bước

Một kế hoạch chuyển đổi an toàn nên đặt trọng tâm vào **hợp đồng container và behavior**, không chỉ đổi cú pháp.

### Bước 1 — Xác định ranh giới XML cần giữ

Ví dụ dự án có ba file:

```text
legacy/
├── repositories.xml
├── integrations.xml
└── properties.xml
```

Phân loại:

- Bean hạ tầng nào cần giữ vì dùng namespace/integration đặc thù?
- Bean nào là class ứng dụng có thể dùng `@Service`/`@Repository` hoặc Java `@Bean` rõ ràng?
- Bean names, aliases, qualifiers và scope nào đang được consumer XML/Java tham chiếu?
- Có tài nguyên/lifecycle nào cần test chính xác không?

Không cần đổi tất cả cùng lúc.

### Bước 2 — Tạo một root Java configuration

```java
@Configuration(proxyBeanMethods = false)
@ImportResource({
    "classpath:legacy/repositories.xml",
    "classpath:legacy/integrations.xml",
    "classpath:legacy/properties.xml"
})
class RootConfig {
}
```

Bây giờ có thể khởi động từ `AnnotationConfigApplicationContext(RootConfig.class)`, trong khi bean definitions cũ vẫn đến từ XML.

### Bước 3 — Chuyển một bean mỗi lần

Giả sử XML có:

```xml
<bean id="catalogRepository"
      class="com.example.catalog.JdbcCatalogRepository"/>
```

Khi chuyển sang Java:

```java
@Bean("catalogRepository")
CatalogRepository catalogRepository(DataSource dataSource) {
    return new JdbcCatalogRepository(dataSource);
}
```

Đây là minh họa; cần các class ứng dụng và `DataSource` có sẵn. **Không để cả XML và Java cùng đăng ký bean name `catalogRepository` trong cùng context** mà chưa có chính sách rõ. Hãy xóa/loại định nghĩa XML đã chuyển trong cùng thay đổi có kiểm chứng.

Điều quan trọng là **giữ đúng bean name** khi các consumer cũ còn tham chiếu `ref="catalogRepository"`.

### Bước 4 — Giữ scope, qualifiers và lifecycle tương đương

Chuyển:

```xml
<bean id="cacheClient"
      class="com.example.CacheClient"
      init-method="connect"
      destroy-method="close"/>
```

thành ý tưởng:

```java
@Bean(name = "cacheClient",
      initMethod = "connect",
      destroyMethod = "close")
CacheClient cacheClient() {
    return new CacheClient();
}
```

Đây là ví dụ cấu trúc, cần class `CacheClient` tương ứng. Nếu bean cũ là prototype, lazy hoặc có property injection/constructor arguments đặc biệt, phải chuyển những metadata này có chủ đích thay vì chỉ copy tên.

### Bước 5 — Kiểm thử context sau mỗi bước

Xác nhận:

- Bean names chính/aliases tồn tại đúng.
- Số lượng bean theo các interface không vô tình tăng.
- Dependency Java ↔ XML còn resolve đúng.
- Init/destroy callbacks không bị gọi hai lần hoặc mất.
- Profiles/properties/conditionals giống yêu cầu môi trường.
- Những class đã được chuyển không còn đăng ký trùng.
- Không có kết nối ngoài hoặc workflow nghiệp vụ vô tình chạy khi test context.

### Bước 6 — Chỉ loại @ImportResource khi không còn lý do

Nếu XML không còn định nghĩa bean cần thiết và không còn XML namespace processor phải giữ, bỏ `@ImportResource` cùng tài nguyên không còn dùng. Nếu ứng dụng vẫn cần một file XML ổn định, giữ nó là một lựa chọn kỹ thuật có thể chấp nhận.

## 15. Những lỗi thường gặp và cách sửa

| Triệu chứng | Nguyên nhân thường gặp | Cách khắc phục |
| --- | --- | --- |
| `FileNotFoundException`/`BeanDefinitionStoreException` khi startup | `@ImportResource` trỏ sai classpath/file | Đối chiếu thư mục `src/main/resources` và runtime JAR; sửa location |
| XML parse error hoặc namespace không được nhận diện | Sai `xmlns`, `xsi:schemaLocation`, thiếu dependency cung cấp namespace handler | Sửa schema/namespace và dependency |
| `CannotLoadBeanClassException` | `class="..."` sai package, nested class name hoặc JAR thiếu | Sửa tên fully qualified/binary class và runtime classpath |
| `NoSuchBeanDefinitionException` | XML chưa import, bean Java chưa register, `ref`/qualifier sai | Kiểm tra root `@Configuration` và tên/kiểu bean |
| `UnsatisfiedDependencyException` | Java factory parameter cần bean XML không tồn tại hoặc không phù hợp | Xem bean XML có được đăng ký, đúng exposed type/qualifier không |
| `NoUniqueBeanDefinitionException` | XML và Java đăng ký nhiều beans cùng interface không có ứng viên thắng | `@Qualifier`/`@Primary`/registry phù hợp, loại bean trùng nếu vô tình |
| Bean name collision | XML `id` trùng `@Bean(name=...)` | Đặt tên rõ; di trú cùng lúc bỏ definition cũ |
| `ref="messageService"` không được giải quyết | Bean Java thực tế có tên khác hoặc configuration không được nạp | Giữ explicit name hoặc sửa ref |
| Bean có property nhưng không được set | Sai tên `<property>`/setter hoặc conversion không phù hợp | Kiểm tra JavaBean setter và property type |
| Placeholder `${app.label}` không giải quyết | Không nạp property placeholder config hoặc property file missing | Xác minh `@ImportResource` XML, namespace context và file properties |
| Alias bị coi là bean thứ hai | Nhầm `<alias>` với `<bean>` | Dùng `getBeanNamesForType`/`isAlias` để xác nhận |
| Scope/proxy không như mong đợi | Tự tạo context thứ hai hay tự gọi `new` ngoài container | Giữ một context, lấy managed beans qua DI |
| XML parse thành công nhưng logic không chạy đúng | Mismatched constructor/property types, lifecycle, config values | Integration test behavior thay vì chỉ kiểm tra file tồn tại |
| Test pass trên IDE nhưng lỗi khi đóng gói | Resource path chỉ đúng trong source tree hoặc JAR thiếu XML | Test artifact runtime classpath và cấu hình Maven resource |
| Class service bị đăng ký hai lần | Vừa `@ComponentScan` class, vừa khai báo `<bean>` XML | Giữ một bean definition hoặc đặt tên/qualifier rõ nếu cố ý |
| Security concern khi load XML từ nguồn ngoài | Tài nguyên cấu hình không đáng tin, thay đổi ngoài kiểm soát | Giới hạn đường dẫn và nguồn XML, review triển khai và dependency |

### Quy trình chẩn đoán gọn

1. **Metadata:** `@ImportResource` có nằm trên class cấu hình đã được Spring register/import/scanned đúng không?
2. **Location:** file có thật trên **runtime classpath** hoặc filesystem được cấu hình?
3. **XML:** cú pháp namespace/schema, `class` name, `id`, `ref`, `property` có hợp lệ?
4. **Bean definitions:** bean có được đăng ký không? Có trùng tên hoặc type ambiguity không?
5. **Dependency:** Java parameter và XML `ref` có trỏ đúng type/name không?
6. **Lifecycle/scope:** bean có lazy/prototype/proxy hay init callback cần chú ý không?
7. **Readiness:** resource ngoài đã được cấu hình an toàn chưa? Đừng chạy nghiệp vụ nặng ngay trong XML/Java factory.
8. **Test:** dùng một ApplicationContext thật để xác nhận cả hai chiều wiring và output.

## 16. Kiểm thử cấu hình lai

### 16.1. Không chỉ kiểm tra XML file tồn tại

Một XML hợp lệ về mặt cú pháp có thể chứa class name sai, property setter không có, bean ref thiếu, hoặc một thứ tự khởi tạo gây vòng phụ thuộc. Vì vậy test cần **tạo ApplicationContext** và lấy các bean quan trọng.

Trong dự án mục 7:

```java
// Đoạn kiểm tra bổ sung, đặt bên trong try có context.
if (!context.containsBean("xmlStore")) {
    throw new AssertionError("XML store missing");
}
if (!context.containsBean("messageService")) {
    throw new AssertionError("Java service missing");
}
if (context.getBean("xmlStore") !=
        context.getBean("legacyStore")) {
    throw new AssertionError("Alias must resolve same singleton");
}
if (!"stored[XML:order-42]".equals(
        context.getBean(MessageService.class).submit("order-42"))) {
    throw new AssertionError("Cross-config wiring incorrect");
}
```

Nếu các điều kiện đúng, đoạn assert không in thêm stdout. Đây là **bổ sung tùy chọn**, không nằm trong output mặc định của `main`.

### 16.2. Kiểm tra chiều tham chiếu XML → Java

```java
// Cũng đặt bên trong try có context của mục 7.
String status = context.getBean(XmlDashboard.class).status();
if (!"xml->java:stored[XML:dashboard]".equals(status)) {
    throw new AssertionError("XML reference to Java bean failed");
}
```

Test này xác nhận `<property ... ref="messageService"/>` trong XML tìm đúng bean `@Bean` của Java.

### 16.3. Kiểm tra không đăng ký trùng

```java
// Dùng MessageStore từ ví dụ đầy đủ.
String[] stores = context.getBeanNamesForType(MessageStore.class);
if (stores.length != 1 || !"xmlStore".equals(stores[0])) {
    throw new AssertionError("Unexpected MessageStore definitions");
}
```

Chỉ có một bean `MessageStore` trong ví dụ chính. Nếu sau khi migration một Java `@Bean` mới tạo thêm một `MessageStore`, test sẽ phát hiện ứng viên tăng lên thay vì coi alias là bean thứ hai.

### 16.4. Unit test không cần XML cho logic thuần Java

Việc cấu hình lai không buộc mọi unit test phải đọc XML. `MessageService` chỉ cần `MessageStore` và `PrefixFormatter`; có thể tạo fake implementations và gọi business method bằng Java thuần. Dùng **integration test** để chứng minh Spring wiring và resource import hoạt động, còn unit test để kiểm tra logic của service nhanh và ổn định.

### 16.5. Tự tạo context bằng Java phải đóng context

```java
try (var context =
        new AnnotationConfigApplicationContext(AppConfig.class)) {
    // Assertions trên bean XML và bean Java.
}
```

`try-with-resources` giúp gọi `close()` ngay cả khi assert thất bại, giảm rò rỉ tài nguyên ở integration test.

## 17. Bài tập thực hành

### Bài 1 — Import một file

File `src/main/resources/legacy/orders.xml` chứa bean `orderRepository`. Viết annotation trên class `@Configuration` để Spring nhập file đó từ classpath.

**Đáp án:**

```java
@Configuration(proxyBeanMethods = false)
@ImportResource("classpath:legacy/orders.xml")
class OrdersConfig {
}
```

Không dùng đường dẫn `src/main/resources/...` trong resource location.

### Bài 2 — Phân biệt @Import và @ImportResource

Bạn có class `MonitoringConfig` chứa `@Bean` methods và file `legacy/metrics.xml` chứa `<bean>` definitions. Nên dùng annotation nào cho mỗi nguồn?

**Đáp án:** `@Import(MonitoringConfig.class)` cho Java class; `@ImportResource("classpath:legacy/metrics.xml")` cho XML file. Có thể dùng cả hai trên cùng root config.

### Bài 3 — Nạp nhiều XML files

Hai resource `legacy/a.xml` và `legacy/b.xml` cùng tồn tại. Viết `@ImportResource` nhập cả hai.

**Đáp án:**

```java
@ImportResource(locations = {
    "classpath:legacy/a.xml",
    "classpath:legacy/b.xml"
})
```

Cả hai đóng góp bean definitions vào cùng context, không có context riêng cho từng file.

### Bài 4 — XML bean → Java @Bean

XML khai báo `<bean id="reportRepository" class="..."/>`. Một Java factory cần instance theo interface `ReportRepository`. Làm sao inject đúng mà không tạo context thứ hai?

**Đáp án:**

```java
@Bean
ReportService reportService(
        @Qualifier("reportRepository") ReportRepository repository) {
    return new ReportService(repository);
}
```

Đây là mẫu minh họa; phải có `ReportRepository`/`ReportService` phù hợp và import `@Qualifier`. Spring dùng BeanFactory hiện tại để resolve dependency, không cần gọi `ClassPathXmlApplicationContext` trong factory.

### Bài 5 — Java @Bean → XML ref

`@Bean("priceCalculator")` tạo bean `PriceCalculator`. XML của `LegacyCheckout` có setter `setPriceCalculator`. Viết property injection tương ứng.

**Đáp án:**

```xml
<property name="priceCalculator" ref="priceCalculator"/>
```

Tên `priceCalculator` phải đúng bean name của Java factory, còn setter phải nhận kiểu tương thích.

### Bài 6 — Alias có tạo bean mới không?

XML:

```xml
<bean id="primaryClient" class="com.example.Client"/>
<alias name="primaryClient" alias="oldClient"/>
```

Có bao nhiêu bean definitions? Với singleton, `getBean("primaryClient") == getBean("oldClient")` là gì?

**Đáp án:** Một bean definition và một alias; hai tên trả cùng singleton reference nên phép so sánh là `true` trong cùng container, nếu bean được tạo thành công.

### Bài 7 — Refactor tên method @Bean

XML cũ tham chiếu `ref="paymentService"`. Java `@Bean PaymentService paymentService()` được đổi tên method thành `createPaymentService()`. Cách nào giữ XML không phải sửa?

**Đáp án:** Dùng `@Bean("paymentService")` trên factory method mới để giữ explicit bean name cũ.

### Bài 8 — Duplicate bean

XML và Java cùng khai báo `id/name="customerRepository"`. Có nên dựa vào “nguồn nào đọc cuối” để chọn implementation mong muốn?

**Đáp án:** Không. Hãy xóa definition cũ khi chuyển, hoặc cấu hình override thật minh bạch với integration test và lý do hợp lệ. Tránh collision không chủ đích.

### Bài 9 — Resource path trong JAR

`@ImportResource("src/main/resources/legacy.xml")` chạy ở thư mục project nhưng lỗi sau khi đóng gói JAR. Sửa thế nào?

**Đáp án:** Đặt file ở `src/main/resources/legacy.xml` và import bằng `@ImportResource("classpath:legacy.xml")` để dùng resource path trên runtime classpath.

### Bài 10 — Placeholder chưa resolve

XML có `<context:property-placeholder location="classpath:settings.properties"/>` và Java có `@Value("${app.timeout}")`. Nhưng bean tạo lỗi vì thiếu property. Kiểm tra những gì?

**Đáp án:** File `settings.properties` có thật trên runtime classpath, key `app.timeout` tồn tại, XML namespace context được khai báo đúng, XML được `@ImportResource` nhập, không có các property processors xung đột, kiểu target có thể convert và cấu hình môi trường đúng.

### Bài 11 — Kết quả chương trình

Trong ví dụ mục 7, vì sao `storeBeanCount=1` dù có hai tên `xmlStore` và `legacyStore`?

**Đáp án:** `legacyStore` chỉ là alias tới `xmlStore`. `getBeanNamesForType(MessageStore.class)` đếm các bean definitions tương thích, không đếm alias như bean độc lập.

### Bài 12 — Chiến lược migration

Ứng dụng còn 50 XML bean definitions nhưng 10 bean đơn giản có thể chuyển sang Java. Hãy nêu trình tự hạn chế regression.

**Đáp án định hướng:** Tạo root Java config có `@ImportResource`; ghi nhận bean names/aliases/scopes/dependencies/lifecycle; chuyển từng nhóm sang `@Bean` hoặc stereotype; xóa definitions XML tương ứng để tránh trùng; giữ tên public khi consumer cũ cần; chạy integration tests sau mỗi bước; chỉ gỡ `@ImportResource` khi không còn XML cần thiết.

## 18. Checklist áp dụng thực tế

- [ ] `@ImportResource` nằm trên class cấu hình **được Spring xử lý**, không phải class không bao giờ được đăng ký.
- [ ] Đường dẫn resource đúng runtime classpath/filesystem và phù hợp khi đóng gói JAR.
- [ ] XML có `<beans>` root, namespaces/schema đúng và bean class names hợp lệ.
- [ ] Các thư viện cung cấp XML namespace handlers có trên classpath.
- [ ] Java `@Bean` và XML `<bean>` không tạo trùng tên/kiểu ngoài ý định.
- [ ] Bean name/alias công khai được giữ tương thích khi refactor/migration.
- [ ] Java factory methods nhận dependency XML qua **method parameters** thay vì tự tạo một ApplicationContext mới.
- [ ] XML `ref` trỏ đúng bean names do Java `@Bean` hoặc các nguồn metadata khác đăng ký.
- [ ] Đã xác nhận `scope`, `lazy-init`, `init/destroy` semantics cho các bean legacy.
- [ ] Placeholder processors/`@Value` hoạt động với property files đúng môi trường.
- [ ] Các bean chưa có trên runtime classpath không bị “che” chỉ nhờ file XML còn tồn tại.
- [ ] Integration test mở một context, kiểm tra hai chiều Java ↔ XML wiring, alias identity và ứng viên theo type.
- [ ] Tài nguyên ngoài/bean lifecycle được cleanup qua context close, không tạo/leak context thứ hai.
- [ ] Sau migration không còn các file XML không được sử dụng hoặc references cũ chưa được sửa.
- [ ] Không đưa tài nguyên XML không tin cậy vào đường nạp cấu hình của hệ thống.

## Tổng kết

`@ImportResource` là cầu nối **Java-centric configuration ↔ XML bean definitions** trong Spring Core Container. Annotation đọc XML resources và nhập bean definitions vào **cùng ApplicationContext**, nhờ đó:

- Java `@Bean` methods có thể nhận dependencies từ XML thông qua dependency injection bình thường.
- XML `<bean>` có thể dùng `ref` để trỏ tới bean do Java `@Bean` khai báo.
- Tên chính, aliases, scope và lifecycle được quản lý bởi cùng BeanFactory.
- Dự án có thể chuyển đổi từ XML sang Java theo từng phần, không cần thay toàn bộ trong một lần.

Hãy dùng resource paths chính xác, giữ public bean-name contracts, tránh duplicate definitions và test cả hai chiều wiring. Khi không còn XML hoặc XML namespace thực sự cần thiết, bạn có thể loại `@ImportResource` và duy trì cấu hình bằng Java thuần.

## Tài liệu Spring chính thức

- [Spring Framework 6.2 — Composing Java-based Configurations](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/composing-configuration-classes.html): mục **Combining Java and XML Configuration**, ví dụ Java-centric `@ImportResource` và XML-centric integration.
- [Spring Framework 6.2 — ImportResource Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/context/annotation/ImportResource.html): `value`, `locations`, `reader` và lựa chọn bean definition reader.
- [Spring Framework 6.2 — The IoC Container: XML Bean Definition Basics](https://docs.spring.io/spring-framework/reference/6.2/core/beans/basics.html): cấu trúc `<beans>`/`<bean>`, XML imports và resource locations.
- [Spring Framework 6.2 — Using the @Bean Annotation](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/bean-annotation.html): parameter injection và bean metadata.
- [Spring Framework 6.2 — Using PropertyPlaceholderConfigurer](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-placeholderconfigurer.html): property placeholders và integration.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="configuration-composition-005">Chọn cấu hình bằng ImportSelector</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn cấu hình bằng `ImportSelector` trong Spring Framework Core Container

## 1. Bài toán: cùng một ứng dụng nhưng không phải lúc nào cũng dùng cùng một cấu hình

Một ứng dụng có thể dùng bộ lưu trữ in-memory khi chạy thử, kết nối thực khi chạy production, hoặc bật một nhóm bean riêng theo thuộc tính cấu hình. Nếu luôn `@Import` tất cả các configuration classes, Spring có thể phải đăng ký nhiều bean cùng interface, gây mơ hồ khi inject, hoặc khởi tạo thành phần không cần thiết.

Giả sử hai configuration classes:

```java
@Configuration(proxyBeanMethods = false)
class LocalShippingConfig {
    @Bean
    ShippingClient shippingClient() {
        return new LocalShippingClient();
    }
}

@Configuration(proxyBeanMethods = false)
class RemoteShippingConfig {
    @Bean
    ShippingClient shippingClient() {
        return new RemoteShippingClient();
    }
}
```

Đây là **đoạn minh họa**: các kiểu `ShippingClient`, `LocalShippingClient` và `RemoteShippingClient` cần được khai báo trong ứng dụng. Cả hai factory methods đều tạo bean kiểu `ShippingClient`, nhưng ứng dụng muốn **chọn một cấu hình phù hợp** thay vì nhập cả hai.

`org.springframework.context.annotation.ImportSelector` chính là một extension point dùng cho việc này. Một selector phân tích metadata của class khai báo `@Import`, chọn danh sách class cần nhập và trả về tên class đầy đủ. Spring tiếp tục xử lý những class được chọn như import candidates.

Cách sử dụng trực tiếp:

```java
@Configuration
@Import(ShippingConfigSelector.class)
class AppConfig {
}
```

Trong ví dụ này, `ShippingConfigSelector` là một class **implement `ImportSelector`** và có nhiệm vụ chọn `LocalShippingConfig` hoặc `RemoteShippingConfig`. Spring không tự biết chọn cái nào chỉ vì hai class cùng tồn tại trên classpath.

Mục tiêu của bài học:

- Nắm chính xác contract của `ImportSelector` và `selectImports(AnnotationMetadata)`.
- Đọc metadata annotation, annotation attributes và `Environment` để quyết định import.
- Tạo custom `@Enable...` annotation phối hợp với `@Import(Selector.class)`.
- Phân biệt selector với `@Import` tĩnh, `@Conditional`, `@Profile`, `@ComponentScan`, `DeferredImportSelector` và `ImportBeanDefinitionRegistrar`.
- Hiểu thời điểm lựa chọn class so với thời điểm khởi tạo bean và cách tránh xung đột.
- Chạy một ví dụ hoàn chỉnh Java 17+ với Spring Framework 6.2, gồm cả trường hợp property override.
- Biết cách kiểm thử, debug, xử lý trường hợp thiếu cấu hình và những lỗi dễ gặp khi viết selector.

## 2. `ImportSelector` là gì và Spring gọi nó khi nào?

`ImportSelector` là interface thuộc package `org.springframework.context.annotation`. Chữ ký trung tâm:

```java
public interface ImportSelector {
    String[] selectImports(AnnotationMetadata importingClassMetadata);
}
```

Đây là chữ ký **rút gọn**, interface thực còn có extension method như `getExclusionFilter()`. `AnnotationMetadata` thuộc `org.springframework.core.type`. Một implementation hợp lệ phải trả mảng **tên class đầy đủ** (fully qualified class names) của các import candidates.

Ví dụ tối thiểu:

```java
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;

public class FixedShippingSelector implements ImportSelector {
    @Override
    public String[] selectImports(AnnotationMetadata metadata) {
        return new String[] { LocalShippingConfig.class.getName() };
    }
}
```

Đoạn này là **minh họa riêng** (cần class `LocalShippingConfig`). Nó tương đương về kết quả chọn class với `@Import(LocalShippingConfig.class)` nhưng tạo điểm mở rộng để logic phức tạp hơn được thêm sau này.

### 2.1. Chu trình xử lý

```text
@Configuration / custom @Enable... được đăng ký
                         |
                         v
               ConfigurationClassParser
                         |
                         v
          Phát hiện @Import(Selector.class)
                         |
                         v
            Tạo instance ImportSelector
                         |
                         v
         Cung cấp các Aware dependencies
          (ví dụ EnvironmentAware)
                         |
                         v
     selectImports(AnnotationMetadata)
                         |
                  String[] FQCN
                         |
                         v
           Spring xử lý các class được chọn
                         |
            +------------+-------------+
            |                          |
            v                          v
   @Configuration classes       Regular component classes
            |                          |
            +------------+-------------+
                         |
                         v
                 Bean definitions
                         |
                         v
                 BeanFactory tạo beans
              theo DI, scope và lifecycle
```

**Điểm phân biệt quan trọng:** selector chọn **class definitions tham gia cấu hình**, không trực tiếp trả object bean. Logic trong `selectImports` diễn ra ở giai đoạn phân tích configuration, thường trước khi các singleton nghiệp vụ được tạo.

Không gọi `new ShippingClient()` trong `selectImports` với kỳ vọng đó là bean được quản lý. Việc đăng ký bean definitions và tạo instances phải do Spring thực hiện qua các class được import hoặc một extension point thích hợp.

### 2.2. Kết quả trả về phải là class name

Đúng:

```java
return new String[] {
    LocalShippingConfig.class.getName(),
    MetricsConfig.class.getName()
};
```

Sai:

```java
return new String[] { "shippingClient", "localShippingConfig" };
```

Hai đoạn là **mã minh họa**; `MetricsConfig` cần tồn tại để đoạn đầu compile. Phần tử của mảng cần là tên class hợp lệ, ví dụ `com.example.config.LocalShippingConfig`, không phải Spring bean name hoặc đường dẫn resource XML. Đối với nested class, dùng `Class.getName()` đáng tin hơn việc tự ghép tên theo ký hiệu dấu chấm, vì tên nhị phân của nested class trong Java có ký tự `$`.

Nếu không cần import gì, trả về mảng rỗng:

```java
return new String[0];
```

Đừng trả `null` để diễn đạt “không có import”: contract của method là mảng. Mảng rỗng là cách biểu đạt rõ ràng và tránh lỗi xử lý bất ngờ.

### 2.3. ImportSelector có thể chọn loại class nào?

Các class selector trả về tiếp tục được Spring xử lý theo semantics của `@Import`:

- `@Configuration` class có các factory methods `@Bean`;
- regular component class có constructor thích hợp;
- các loại class được Spring import parser hỗ trợ trong các tình huống nâng cao.

Trong hầu hết mã ứng dụng, selector nên trả **configuration class names**, để lựa chọn một gói bean definitions có thể được kiểm thử và quản lý theo module. Một selector quá phức tạp, trả rất nhiều implementation classes mà không có contract rõ, thường khó bảo trì hơn các configuration entry points.

## 3. `AnnotationMetadata`: đọc dữ liệu mà không instantiate class cấu hình

Tham số `importingClassMetadata` cho biết metadata của **class đang khai báo hoặc mang gián tiếp `@Import`**, không phải object configuration bean đã khởi tạo.

Ví dụ:

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(ShippingConfigSelector.class)
public @interface EnableShipping {
    String mode() default "local";
}
```

Và ứng dụng:

```java
@Configuration
@EnableShipping(mode = "remote")
class AppConfig {
}
```

Đây là các đoạn minh họa; cần imports từ `java.lang.annotation` và Spring, cũng như implementation của selector. Với `AnnotationMetadata` được truyền vào selector, Spring có thể truy xuất attribute `mode` mà không tạo instance của `AppConfig`.

### 3.1. Đọc attributes của custom annotation

```java
@Override
public String[] selectImports(AnnotationMetadata metadata) {
    Map<String, Object> attributes =
            metadata.getAnnotationAttributes(EnableShipping.class.getName());

    if (attributes == null) {
        throw new IllegalStateException("@EnableShipping is missing");
    }

    String mode = (String) attributes.get("mode");
    return switch (mode) {
        case "local" -> new String[] { LocalShippingConfig.class.getName() };
        case "remote" -> new String[] { RemoteShippingConfig.class.getName() };
        default -> throw new IllegalArgumentException("Unsupported mode: " + mode);
    };
}
```

Đây là **trích đoạn minh họa**, không độc lập. `Map` cần import `java.util.Map` và các class config phải tồn tại.

Hãy phân biệt:

- `metadata.getClassName()`: class name của **importing configuration class**.
- `metadata.hasAnnotation(...)` / `metadata.hasMetaAnnotation(...)`: kiểm tra annotation metadata theo API tương ứng.
- `metadata.getAnnotationAttributes(...)`: lấy các attributes của annotation được chỉ định.
- Tên class trả về từ `selectImports`: **imported configuration/component classes**, có thể hoàn toàn khác class name của metadata đầu vào.

Khi `@EnableShipping` là một composed annotation mang `@Import`, metadata đọc attributes của `@EnableShipping` trên class sử dụng annotation. Có thể dùng `AnnotationAttributes` khi muốn các accessor có kiểm tra kiểu hoặc xử lý metadata phức tạp; ví dụ runnable ở mục 8 dùng enum và cast rõ kiểu để tránh nhập nhằng.

### 3.2. Tham số class metadata không phải bean factory

Không thể mặc nhiên dùng `importingClassMetadata` như một `ApplicationContext` để lấy `@Bean` instances. Giai đoạn này vẫn đang chọn những classes tạo nên configuration graph. Nếu cần thông tin cấu hình, ưu tiên environment/properties và annotation metadata thay vì lookup các singleton nghiệp vụ chưa chắc tồn tại.

**Không dựa vào việc gọi constructor một configuration class** từ selector để “đọc annotation attributes”: constructor có thể có dependency injection, side effects hoặc lifecycle cần container. Metadata giúp tránh điều đó.

## 4. Nhận `Environment`, `BeanFactory` và những dependency hạ tầng khác

Theo Javadoc Spring Framework 6.2, `ImportSelector` có thể nhận một số hạ tầng của container **trước khi** `selectImports(...)` được gọi, chẳng hạn thông qua những Aware interfaces:

- `EnvironmentAware`;
- `BeanFactoryAware`;
- `BeanClassLoaderAware`;
- `ResourceLoaderAware`.

Spring cũng hỗ trợ constructor injection với một constructor duy nhất có những parameter types hạ tầng thích hợp, ví dụ `Environment`, `BeanFactory`, `ClassLoader` hoặc `ResourceLoader`, theo API được tài liệu hóa.

### 4.1. `EnvironmentAware` chọn theo property

```java
public final class ShippingConfigSelector
        implements ImportSelector, EnvironmentAware {

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public String[] selectImports(AnnotationMetadata metadata) {
        String mode = environment.getProperty(
                "shipping.provider", "local");

        return switch (mode) {
            case "local" ->
                    new String[] { LocalShippingConfig.class.getName() };
            case "remote" ->
                    new String[] { RemoteShippingConfig.class.getName() };
            default ->
                    throw new IllegalArgumentException("Invalid provider: " + mode);
        };
    }
}
```

Đây là **đoạn minh họa** (imports `EnvironmentAware` và `Environment` ở packages `org.springframework.context` / `org.springframework.core.env`). Property có thể đến từ system properties, environment variables hoặc `PropertySource` được đăng ký phù hợp trước quá trình parsing. Logic trên sẽ ném lỗi rõ nếu giá trị không hợp lệ, thay vì âm thầm dùng provider khác với yêu cầu.

**Nên tránh dùng `BeanFactory.getBean(...)` trong selector** chỉ để phân nhánh theo một bean nghiệp vụ. Configuration đang được xây dựng; eager lookup có thể gây circular dependency, thiếu bean definitions hoặc khởi tạo quá sớm. Nếu dữ kiện chỉ là `dev/prod`, feature flag cấu hình hoặc một annotation attribute, `Environment`/metadata thích hợp hơn.

### 4.2. Chọn nguồn nào làm ưu tiên?

Khi hỗ trợ cả annotation attribute và property môi trường, hãy định nghĩa chính sách rõ ràng. Ví dụ chính sách:

1. Attribute trong `@EnableShipping(channel=STANDARD)` cung cấp giá trị mặc định.
2. Property `lesson.shipping.channel` nếu là `standard` hoặc `express` sẽ **ghi đè** attribute.
3. Property `annotation` có nghĩa giữ nguyên giá trị annotation.
4. Giá trị khác được báo lỗi cấu hình, không lặng lẽ rơi sang STANDARD.

Chính sách này được minh họa bằng chương trình hoàn chỉnh ở mục 8. Một ứng dụng khác có thể quyết định annotation luôn thắng property; điều quan trọng là quy tắc phải được tài liệu hóa, kiểm thử và ổn định giữa môi trường.

## 5. Custom `@Enable...` annotation: tổ hợp cấu hình có khả năng tái sử dụng

Nhiều API của Spring tổ chức dưới dạng `@EnableXxx`: người dùng chỉ gắn annotation ở một configuration class, còn thư viện cài đặt một selector/registrar để thêm những bean definitions cần thiết.

Ví dụ:

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(ShippingConfigSelector.class)
public @interface EnableShipping {
    Channel channel() default Channel.STANDARD;
}
```

Ở đây:

- `@Target(ElementType.TYPE)` cho phép gắn trên class;
- `@Retention(RetentionPolicy.RUNTIME)` giữ metadata annotation khi chương trình chạy;
- `@Documented` làm annotation xuất hiện trong tài liệu Javadoc của class sử dụng nó;
- `@Import(ShippingConfigSelector.class)` cung cấp điểm mở rộng xử lý import;
- `channel` là thuộc tính công khai để cấu hình application.

Caller chỉ cần:

```java
@Configuration(proxyBeanMethods = false)
@EnableShipping(channel = Channel.EXPRESS)
class CheckoutConfig {
}
```

Cách này cung cấp **API cấu hình ngắn gọn**: client module không cần nhớ tên của các class `ExpressShippingConfig` và `StandardShippingConfig` nội bộ.

### 5.1. Tránh lộ details của implementation ra annotation

Một annotation như `@EnableShipping(channel=EXPRESS)` mô tả quyết định nghiệp vụ/cấu hình rõ hơn việc yêu cầu caller tự nhập `@Import(ExpressShippingConfig.class)` từ package internal. Khi implementation đổi, thư viện có thể thay cách selector map enum → class mà không buộc application refactor nhiều imports.

Tuy nhiên, không nên bọc mọi cặp configuration classes bằng custom annotation. Nếu một ứng dụng chỉ nhập một cấu hình cố định ở một nơi, `@Import(MyConfig.class)` đơn giản và dễ hiểu hơn.

### 5.2. Tên enum và đổi phiên bản

Nếu annotation công khai định nghĩa enum `Channel.STANDARD`, `Channel.EXPRESS`, việc thêm/xóa/đổi các giá trị có thể ảnh hưởng source compatibility và chính sách runtime. Đừng tự động chọn fallback “gần đúng” khi nhận giá trị enum/property lạ; thay vào đó báo lỗi rõ và cung cấp migration path phù hợp.

## 6. `ImportSelector` khác các lựa chọn cấu hình phổ biến như thế nào?

| Cơ chế | Điều kiện chọn | Tạo ra điều gì? | Khi phù hợp |
| --- | --- | --- | --- |
| `@Import(AConfig.class)` | Cố định trong source | Nhập class cấu hình cụ thể | Tổ hợp cấu hình tĩnh, ít nhánh |
| `ImportSelector` | Code chọn theo annotation metadata, environment, v.v. | Tên classes để Spring tiếp tục import | Custom `@Enable...`, chọn module/config |
| `@Profile` | Active profiles | Bật/tắt config hoặc bean definition | Cấu hình khác nhau theo môi trường |
| `@Conditional` | `Condition.matches(...)` | Bật/tắt config/bean theo điều kiện | Quy tắc điều kiện cục bộ |
| `@ComponentScan` | Package roots và filters | Phát hiện các component candidates | Tập component thuộc vùng ứng dụng |
| `ImportBeanDefinitionRegistrar` | Logic ở registry level | Đăng ký bean definitions trực tiếp | Framework extension phức tạp |
| `DeferredImportSelector` | Chọn ở giai đoạn deferred processing | Classes nhập sau khi configs thường được xử lý | Auto-configuration/nhóm selectors nâng cao |

### 6.1. Khi nào chỉ cần `@Profile`?

Nếu một ứng dụng chỉ muốn bean A trong `dev` và bean B trong `prod`, hai `@Configuration` classes gắn `@Profile("dev")` và `@Profile("prod")` có thể đơn giản hơn viết selector. Tuy nhiên, hãy bảo đảm đúng một cấu hình được kích hoạt khi dependency là bắt buộc.

`ImportSelector` phù hợp hơn khi việc chọn không chỉ phụ thuộc một profile: ví dụ một annotation có nhiều attributes, cần chọn một trong nhiều implementation modules, hoặc library xây public `@Enable...` API.

### 6.2. Khi nào `@Conditional` đơn giản hơn?

`@Conditional(SomeCondition.class)` đánh giá một điều kiện để bỏ qua hoặc giữ một bean/configuration. Nó không cần trả tên class khác. Nếu nhiệm vụ là “đăng ký bean X khi điều kiện đúng”, conditional thường ngắn gọn hơn selector. Nếu nhiệm vụ là “theo lựa chọn, import **toàn bộ một configuration class** tương ứng”, selector có thể rõ ràng hơn.

### 6.3. `@Import` không tự quét package

`@Import(ShippingConfigSelector.class)` không khiến mọi class cùng package với selector thành bean. Spring chỉ xử lý những class được trả về và những registrations/imports phụ thuộc từ các class đó. Nếu một configuration được chọn có `@ComponentScan`, chính scan đó có thể đưa nhiều component vào registry; phạm vi thực tế vì vậy phụ thuộc cấu hình được chọn.

### 6.4. ImportSelector chọn class, không giải quyết tất cả bean candidates

Giả sử selector chọn `RemoteShippingConfig` nhưng application còn có một `@Bean ShippingClient localClient()` từ nguồn khác. Khi một service inject đơn `ShippingClient`, vẫn có thể có hai candidates và phát sinh `NoUniqueBeanDefinitionException` nếu không có lựa chọn rõ.

Selector giúp giảm import dư, **không thay thế** các cơ chế `@Qualifier`, `@Primary`, bean naming hay thiết kế dependency hợp lệ.

## 7. `DeferredImportSelector` và những extension points nâng cao

`DeferredImportSelector` kế thừa `ImportSelector` nhưng được xử lý ở giai đoạn **sau** khi các `@Configuration` classes thông thường đã được parser xem xét, thay vì ngay theo trình tự xử lý selector thường. Nó hữu ích khi các imported configurations cần được chọn sau khi đã có thông tin đầy đủ hơn về tập cấu hình.

Một ví dụ tối thiểu:

```java
public class LateShippingSelector implements DeferredImportSelector {
    @Override
    public String[] selectImports(AnnotationMetadata metadata) {
        return new String[] { StandardShippingConfig.class.getName() };
    }
}
```

Đây là **trích đoạn minh họa** (cần import `DeferredImportSelector` và `AnnotationMetadata` cùng class config). Để sử dụng, gắn `@Import(LateShippingSelector.class)` lên một configuration class. Deferred không có nghĩa “chờ tới khi ứng dụng đang chạy mới tự thêm bean” hoặc “tạo bean bất đồng bộ”; nó chỉ thay đổi **giai đoạn xử lý imports trong quá trình bootstrap**.

### 7.1. `DeferredImportSelector.Group`

Các deferred selectors có thể được gom nhóm để phối hợp thứ tự/chính sách xử lý thông qua interface `DeferredImportSelector.Group`. Một nhóm nhận selectors và metadata, rồi cung cấp các import entries. Đây là công cụ framework-level; không cần tự viết group chỉ để chọn STANDARD/EXPRESS ở một ứng dụng nhỏ.

**Đặc biệt:** thứ tự xử lý import selectors **khác** thứ tự khởi tạo singleton beans. Nếu bean B cần bean A, hãy khai báo dependency qua constructor/factory-method parameters; `@Order` trên selectors hoặc import order không thay thế dependency graph.

### 7.2. `getExclusionFilter()`

`ImportSelector` có default method `getExclusionFilter()` trả một predicate dùng để loại những class candidates khỏi tập import theo quy tắc Spring, có thể được áp dụng bắc cầu cho các classes tìm qua imports của selector. Đây là tính năng nâng cao để một selector loại class names trong import processing; nó **không phải** global component-scan exclude filter cho toàn ứng dụng.

Nếu chỉ cần chọn một trong hai configs, trả mảng chứa đúng một class name sẽ dễ kiểm chứng hơn dùng exclusion filter để nhập rất rộng rồi loại dần.

### 7.3. `ImportBeanDefinitionRegistrar` làm gì khác?

Một registrar thao tác trực tiếp với `BeanDefinitionRegistry` để đăng ký definitions, thường dùng trong library xây infrastructure bean động. Trong khi đó, selector chỉ trả class names để Spring tiếp tục xử lý theo `@Import`.

Nếu yêu cầu là “dựa vào `channel` để chọn một `@Configuration` chứa các `@Bean` methods”, selector là công cụ phù hợp. Nếu yêu cầu là “đăng ký số lượng bean definitions với types, bean names và metadata tùy biến cao theo annotation attributes”, registrar có thể phù hợp hơn nhưng khó triển khai/kiểm thử hơn.

## 8. Ví dụ Java 17+ hoàn chỉnh: chọn cấu hình shipping

Chương trình này chạy trên **Spring Framework 6.2.19**, không cần Spring Boot hoặc server. Nó thể hiện:

1. `@EnableShipping(channel=STANDARD)` chọn `StandardShippingConfig`.
2. `@EnableShipping(channel=EXPRESS)` chọn `ExpressShippingConfig`.
3. Property `lesson.shipping.channel=express` ưu tiên hơn attribute STANDARD, nên chọn EXPRESS.
4. Không dùng `@EnableShipping` thì không có bean `Carrier` nếu không đăng ký bằng cách khác.
5. Mỗi context có đúng **một** bean `Carrier`; `CheckoutService` lấy bean đó từ phương thức `@Bean` có tham số.

### 8.1. Cấu trúc

```text
spring-import-selector-demo/
├── settings.gradle
├── build.gradle
└── src/
    └── main/
        └── java/
            └── ImportSelectorDemo.java
```

File `settings.gradle`:

```groovy
rootProject.name = 'spring-import-selector-demo'
```

File `build.gradle`:

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
    mainClass = 'ImportSelectorDemo'
}
```

Dùng JDK 17+ và phiên bản Gradle tương thích. `spring-context` mang theo `spring-beans` và `spring-core` cần cho `ImportSelector`, metadata, `EnvironmentAware` và `AnnotationConfigApplicationContext`.

### 8.2. Mã Java đầy đủ

Tạo file `src/main/java/ImportSelectorDemo.java`:

```java
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Locale;
import java.util.Map;

import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.type.AnnotationMetadata;

public class ImportSelectorDemo {

    public enum Channel {
        STANDARD, EXPRESS
    }

    public interface Carrier {
        String code();
        int surcharge();
    }

    public record StandardCarrier() implements Carrier {
        @Override
        public String code() {
            return "standard";
        }

        @Override
        public int surcharge() {
            return 5;
        }
    }

    public record ExpressCarrier() implements Carrier {
        @Override
        public String code() {
            return "express";
        }

        @Override
        public int surcharge() {
            return 20;
        }
    }

    public record CheckoutService(Carrier carrier) {
        public int total(int subtotal) {
            return subtotal + carrier.surcharge();
        }
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @Import(ShippingSelector.class)
    public @interface EnableShipping {
        Channel channel() default Channel.STANDARD;
    }

    public static class ShippingSelector
            implements ImportSelector, EnvironmentAware {

        private Environment environment;

        @Override
        public void setEnvironment(Environment environment) {
            this.environment = environment;
        }

        @Override
        public String[] selectImports(
                AnnotationMetadata importingClassMetadata) {

            Map<String, Object> attributes =
                    importingClassMetadata.getAnnotationAttributes(
                            EnableShipping.class.getName());

            if (attributes == null) {
                throw new IllegalStateException(
                        "@EnableShipping is required");
            }

            Channel channel = (Channel) attributes.get("channel");

            String configured = environment.getProperty(
                    "lesson.shipping.channel", "annotation");
            switch (configured.toLowerCase(Locale.ROOT)) {
                case "annotation" -> {
                    // Giữ giá trị từ @EnableShipping.
                }
                case "standard" -> channel = Channel.STANDARD;
                case "express" -> channel = Channel.EXPRESS;
                default -> throw new IllegalArgumentException(
                        "Unsupported shipping channel: " + configured);
            }

            Class<?> selected = channel == Channel.EXPRESS
                    ? ExpressShippingConfig.class
                    : StandardShippingConfig.class;
            return new String[] { selected.getName() };
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class StandardShippingConfig {
        @Bean
        Carrier carrier() {
            return new StandardCarrier();
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ExpressShippingConfig {
        @Bean
        Carrier carrier() {
            return new ExpressCarrier();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableShipping(channel = Channel.STANDARD)
    public static class StandardAppConfig {
        @Bean
        CheckoutService checkoutService(Carrier carrier) {
            return new CheckoutService(carrier);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableShipping(channel = Channel.EXPRESS)
    public static class ExpressAppConfig {
        @Bean
        CheckoutService checkoutService(Carrier carrier) {
            return new CheckoutService(carrier);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class PlainAppConfig {
        // Không có @Import hay @EnableShipping.
    }

    private static void run(
            String name, Class<?> appConfig, String selectedProperty) {

        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource(
                            "lesson-case",
                            Map.of("lesson.shipping.channel",
                                    selectedProperty)));

            context.register(appConfig);
            context.refresh();

            CheckoutService checkout =
                    context.getBean(CheckoutService.class);
            System.out.println(name + ": carrier=" +
                    checkout.carrier().code() +
                    ", total=" + checkout.total(100));
            System.out.println(name + ": carrierCount=" +
                    context.getBeanNamesForType(Carrier.class).length);
        }
    }

    public static void main(String[] args) {
        run("STANDARD", StandardAppConfig.class, "annotation");
        run("EXPRESS", ExpressAppConfig.class, "annotation");
        run("OVERRIDE", StandardAppConfig.class, "express");

        try (var plain =
                new AnnotationConfigApplicationContext(PlainAppConfig.class)) {
            System.out.println("PLAIN: carrierCount=" +
                    plain.getBeanNamesForType(Carrier.class).length);
        }
    }
}
```

Đây là một file Java hoàn chỉnh: `ImportSelectorDemo` là top-level public class; các interfaces, enum, records, annotation, selector và configuration classes được khai báo nested. `Class.getName()` trả đúng tên JVM của nested configs, vì vậy selector không cần tự ghép tên class.

`ShippingSelector` không tạo `Carrier` bằng `new`; nó chỉ quyết định **configuration class** cần import. Sau đó Spring xử lý `@Bean Carrier carrier()` của config được chọn. `CheckoutService` nhận `Carrier` từ container nhờ method parameter injection.

### 8.3. Chạy chương trình

Trong thư mục `spring-import-selector-demo`:

```bash
gradle run --quiet
```

Nếu có Gradle Wrapper, dùng `./gradlew run --quiet` trên Linux/macOS hoặc `.\gradlew.bat run --quiet` trên Windows PowerShell.

**Stdout do chính chương trình in ra:**

```text
STANDARD: carrier=standard, total=105
STANDARD: carrierCount=1
EXPRESS: carrier=express, total=120
EXPRESS: carrierCount=1
OVERRIDE: carrier=express, total=120
OVERRIDE: carrierCount=1
PLAIN: carrierCount=0
```

Các dòng trên là kết quả của `System.out.println`; nếu môi trường cấu hình thêm logging thì Gradle/Spring có thể hiển thị thông tin khác.

### 8.4. Phân tích từng case

**STANDARD:** `StandardAppConfig` mang `@EnableShipping(channel=STANDARD)`. Property `lesson.shipping.channel=annotation` giữ nguyên giá trị annotation, nên selector trả tên `StandardShippingConfig`. Spring đăng ký bean `Carrier` từ factory method của config ấy. Phí thêm là 5, tổng 100 + 5 = 105.

**EXPRESS:** `ExpressAppConfig` mang `@EnableShipping(channel=EXPRESS)` và property vẫn là `annotation`. Selector trả `ExpressShippingConfig`, đăng ký `ExpressCarrier` có phí 20. Tổng bằng 120.

**OVERRIDE:** Cùng class `StandardAppConfig` như STANDARD, nhưng property được đặt thành `express` trước `register` và `refresh`. Selector nhận `Environment`, đọc property và chọn EXPRESS thay cho annotation STANDARD. Cấu hình và bean phụ thuộc được quyết định trong quá trình xây context, không đợi đến lúc gọi `checkout.total(...)`.

**PLAIN:** `PlainAppConfig` không mang `@EnableShipping`, không import hay scan `Carrier`. Vì thế `getBeanNamesForType(Carrier.class)` trả mảng rỗng. Nó cũng không khai báo `CheckoutService`, tránh tạo một consumer bắt buộc phải có Carrier.

**carrierCount = 1:** Trong từng context có selector, chỉ một trong hai configurations shipping được import. Đó là lý do không có ambiguity kiểu `Carrier` ở ví dụ. Nếu ứng dụng còn nguồn khai báo `Carrier` khác, count có thể lớn hơn; selector không tự loại các bean definitions độc lập ấy.

### 8.5. Vì sao dùng `MapPropertySource` thay vì sửa system property toàn JVM?

`MapPropertySource` được thêm vào `Environment` **của từng context** trước khi parsing. Điều này giúp ví dụ có output ổn định, không thay đổi `System.setProperty` chung có thể ảnh hưởng test khác. `addFirst(...)` đưa property source lên đầu thứ tự ưu tiên tìm property trong context được tạo.

Trong dự án thực tế, bạn có thể dùng environment variables, system properties hoặc config files đã được Spring/Boot tải đúng thời điểm. Điều cần nhớ là selector phải đọc giá trị **khi configuration đang được phân tích**: thay đổi một property sau khi context refresh thường **không tự chạy lại `selectImports`** và không thay thế bean definitions hiện có.

## 9. Thiết kế lựa chọn cấu hình an toàn

### 9.1. Chỉ chọn đúng những imports cần thiết

Nếu các cấu hình có factory methods trùng bean name `carrier`, nhập cả `StandardShippingConfig` và `ExpressShippingConfig` vào cùng một context là một thiết kế khác với việc chọn một trong hai. Tùy bean definition overriding policy, có thể xảy ra xung đột hoặc một bean bị ghi đè.

Selector ở mục 8 trả **duy nhất một** tên config để diễn đạt “một channel đang hoạt động”. Không dùng thứ tự imports làm cơ chế mặc định chọn provider.

### 9.2. Fail fast với cấu hình không hợp lệ

Nếu property nhận một giá trị ngoài `annotation`, `standard`, `express`, ví dụ `slow`, ví dụ sẽ ném `IllegalArgumentException` trong quá trình phân tích cấu hình. Điều này được chủ đích: typo trong configuration là lỗi nên báo rõ thay vì âm thầm đổi sang một provider.

```java
default -> throw new IllegalArgumentException(
        "Unsupported shipping channel: " + configured);
```

Đây là trích đoạn của selector ở mục 8, không phải file độc lập.

Nếu tính năng thật sự tùy chọn, bạn có thể cho selector trả mảng rỗng khi flag disable. Nhưng hãy kiểm tra các consumers: một bean `CheckoutService` bắt buộc phải có `Carrier` sẽ vẫn tạo lỗi dependency nếu selector không đăng ký carrier nào. Để module tùy chọn hoạt động trọn vẹn, cần conditional consumer configuration hoặc optional dependency policy tương ứng.

### 9.3. Ưu tiên lựa chọn có thể dự đoán

Viết tài liệu rõ attribute nào và property nào thắng. Trong code mục 8, property override được xét **sau** annotation. Nếu user chỉ đọc annotation và không biết property override tồn tại, kết quả runtime dễ gây bất ngờ. Có thể cân nhắc ghi thông tin lựa chọn vào structured logs ở startup hoặc metrics cấu hình, nhưng không ghi thông tin bí mật như credentials.

### 9.4. Hạn chế side effects trong selector

Tránh thực hiện các việc sau trong `selectImports(...)`:

- gọi HTTP API để quyết định class;
- truy vấn database hoặc các bean nghiệp vụ chưa được khởi tạo;
- ghi file hoặc thay đổi trạng thái global;
- tạo thread/timer để chọn configuration;
- tự khởi tạo bean rồi cache trong field selector;
- chọn class ngẫu nhiên theo thời gian mà không có config ổn định.

Selection nên **nhẹ, xác định được, đọc metadata/environment** để ứng dụng khởi động nhanh, test tái hiện được và không có dependency cycle do bootstrap quá sớm.

### 9.5. Classpath và module dependencies

Selector trả tên class có nghĩa class đó phải có trên runtime classpath và load được theo class loader đang sử dụng. Nếu cấu hình trả `com.vendor.OptionalStorageConfig` nhưng module/JAR tương ứng không có, context có thể thất bại trong import processing. Với optional integrations, trước khi chọn hãy dùng thông tin classpath/environment có chủ đích hoặc dùng `@ConditionalOnClass` trong Spring Boot auto-configuration theo quy ước phù hợp.

Đừng dùng `Class.forName(...)` bừa bãi để khởi tạo mọi class rồi quyết định: nạp class có thể có side effects. Với ứng dụng đơn giản, dependency tường minh và enum → config class là đủ.

### 9.6. Bean names, scopes và qualifiers vẫn quan trọng

`@ImportSelector` không tạo quy tắc singleton mới, không mặc định biến bean thành lazy và không gắn `@Primary` cho cấu hình được chọn. Sau khi class được import, `@Bean`, `@Scope`, `@Lazy`, `@Qualifier`, `@Profile` và các conditions liên quan vận hành như thông thường.

Nếu hai configurations độc lập có cùng interface nhưng chỉ một được chọn, injection theo type thường đơn giản. Nhưng nếu có một bean `Carrier` nữa được đăng ký bằng component scan, selector không tự bảo đảm uniqueness. Hãy kiểm tra toàn bộ registry.

## 10. Những lỗi thường gặp

| Triệu chứng | Nguyên nhân | Cách sửa |
| --- | --- | --- |
| `selectImports` trả `"shippingClient"` | Nhầm Spring bean name với fully qualified class name | Trả `ShippingConfig.class.getName()` |
| Selector không được gọi | Class chứa `@Import`/`@Enable...` không được đăng ký hoặc annotation sai target | Xác nhận configuration entry point và meta-annotations |
| `attributes == null` | Selector gắn ở class không có `@EnableShipping` cần đọc | Kiểm tra metadata và khai báo annotation |
| Cast attribute enum sai kiểu | Giả định attribute là String trong khi khai báo enum | Dùng enum type chính xác hoặc `AnnotationAttributes` |
| Property đổi sau `refresh()` nhưng bean không thay | Selector chọn class khi parsing/bootstrap, không chạy lại tự động | Khởi tạo context mới hoặc cơ chế runtime switching riêng |
| Có hai `Carrier` dù selector chọn một | Một đường registration khác cũng tạo Carrier | Tìm tất cả `@Bean`/`@ComponentScan`/`@Import` liên quan |
| `NoSuchBeanDefinitionException` khi selector trả empty array | Consumer vẫn đòi dependency bắt buộc | Dùng optional dependency/conditional consumer config |
| Class được trả về không tồn tại | Sai tên binary/FQCN hoặc thiếu JAR | Dùng `SelectedConfig.class.getName()` và kiểm tra runtime classpath |
| Selector lấy business bean quá sớm | Import processing xảy ra trước khi context khởi tạo xong | Chỉ đọc metadata/environment hoặc hạ tầng bootstrap được hỗ trợ |
| Bật cả `@Profile` và selector, bean biến mất | Configuration được chọn tiếp tục bị profile/conditional loại | Kiểm tra active profiles và conditions |
| Lẫn `@ImportSelector` với `@ComponentScan` | Selector chỉ chọn class, không scan package | Sử dụng đúng cơ chế cần thiết |
| Nhầm selector với registrar | Selector không trực tiếp đăng ký bean definition tùy ý | Dùng `ImportBeanDefinitionRegistrar` nếu thật sự cần API registry |
| Dùng `DeferredImportSelector` cho nhu cầu đơn giản | Tăng độ phức tạp về ordering/group mà không có lợi ích | Bắt đầu với `ImportSelector` thường |
| Hard-code tên nested class bằng dấu chấm | JVM binary name của nested class chứa dấu `$` | Dùng `Class.getName()` |
| Dùng `@Order` rồi tưởng thứ tự bean init được quyết định | Import selection order không thay dependency graph | Khai báo DI/`@DependsOn` phù hợp |
| Chọn cấu hình theo random/time | Context khó tái hiện, test flaky | Chọn theo input config ổn định |
| Imported config bị scan/import thêm lần nữa | Nguồn bean definitions chồng chéo | Kiểm tra import graph, scanner, bean naming và overriding policy |

### 10.1. Quy trình chẩn đoán 6 bước

1. **Xác nhận selector chạy:** configuration class nào mang `@Import` hoặc `@EnableShipping`, class ấy đã thực sự được context đăng ký chưa?
2. **Xác định metadata:** tên annotation và kiểu attribute có đúng không? Có thể `metadata.getAnnotationAttributes(...)` trả `null` không?
3. **Xác định environment:** property sources đã được thêm **trước refresh** chưa, thứ tự ưu tiên ra sao?
4. **Kiểm tra class names trả về:** dùng `Class.getName()`, bảo đảm class có trên runtime classpath.
5. **Kiểm tra bean definitions cuối:** context thành công rồi thì dùng `getBeanNamesForType(...)` để xem bean của cấu hình được chọn; nếu context fail, đọc root cause thay vì truy vấn context chưa chạy được.
6. **Kiểm tra consumers:** các bean sử dụng dependency có phù hợp với số ứng viên thực tế không? Nếu không chọn provider nào, consumer bắt buộc không thể tự hoạt động.

## 11. Kiểm thử selector mà không dựng toàn bộ ứng dụng

Có hai cấp độ kiểm thử khác nhau.

### 11.1. Unit test selection logic

Nếu quy tắc chỉ phụ thuộc enum và property, trích logic chọn config sang một method thuần nhận input rõ ràng; unit test có thể kiểm tra:

| Annotation channel | Property | Class được chọn |
| --- | --- | --- |
| STANDARD | annotation | StandardShippingConfig |
| EXPRESS | annotation | ExpressShippingConfig |
| STANDARD | express | ExpressShippingConfig |
| EXPRESS | standard | StandardShippingConfig |
| STANDARD | giá trị lạ | Ném lỗi cấu hình |

Kiểm thử đơn vị không nhất thiết phải tạo Spring context. Tuy nhiên nó không chứng minh các `@Bean` definitions có thực sự được đăng ký; cần integration test cho phần đó.

### 11.2. Integration test với context nhỏ

Ví dụ mục 8 tạo ba contexts có lựa chọn khác nhau. Một test tương tự có thể dùng assertions:

```java
try (var context = new AnnotationConfigApplicationContext()) {
    context.getEnvironment().getPropertySources().addFirst(
            new MapPropertySource(
                    "test", Map.of("lesson.shipping.channel", "express")));
    context.register(StandardAppConfig.class);
    context.refresh();

    Carrier carrier = context.getBean(Carrier.class);
    if (!"express".equals(carrier.code())) {
        throw new AssertionError("Expected express carrier");
    }
    if (context.getBeanNamesForType(Carrier.class).length != 1) {
        throw new AssertionError("Expected exactly one Carrier");
    }
}
```

Đây là **trích đoạn** dùng các nested types `StandardAppConfig` và `Carrier` trong `ImportSelectorDemo`; khi đặt vào class khác, cần tên kiểu đầy đủ/import thích hợp. Assertion giúp phát hiện sai lệch thay vì chỉ quan sát stdout. Có thể mở rộng test cho nhánh property không hợp lệ, trường hợp thiếu provider và conflict với một `Carrier` khác.

### 11.3. Vì sao mỗi test nên có context riêng?

Một `ApplicationContext` xử lý bean definitions trong lúc khởi tạo. Nếu bạn muốn kiểm thử hai cấu hình khác nhau, nên tạo **hai context tách biệt**, không đổi property rồi mong cùng context tự gỡ bean cũ và import config mới. Điều này giúp test độc lập, tránh rò state và phản ánh đúng thời điểm chọn import.

## 12. Bài tập tự kiểm tra có đáp án

### Bài 1 — Tên class hay bean name?

Method `selectImports(...)` trả `new String[]{"expressCarrier"}`. Đây là bean name của `@Bean Carrier expressCarrier()`. Có đúng contract không?

**Đáp án:** Không. Nó cần trả tên class để import, ví dụ `ExpressShippingConfig.class.getName()`. Bean name chỉ được quyết định khi Spring xử lý class/config được chọn.

### Bài 2 — Lựa chọn bằng attribute

`@EnableShipping(channel=EXPRESS)` và property `lesson.shipping.channel=annotation`. Selector mục 8 trả class nào?

**Đáp án:** `ExpressShippingConfig`, vì property “annotation” nghĩa là giữ nguyên enum attribute EXPRESS.

### Bài 3 — Property ghi đè annotation

`@EnableShipping(channel=STANDARD)`, property `lesson.shipping.channel=express`. Kết quả nào?

**Đáp án:** `ExpressShippingConfig`. Property có ưu tiên cao hơn attribute trong ví dụ.

### Bài 4 — Vắng mặt tính năng

Selector trả mảng rỗng, nhưng `CheckoutService` có factory method yêu cầu tham số `Carrier carrier` bắt buộc. Context có luôn refresh thành công không?

**Đáp án:** Không. Nếu không có bean Carrier từ nguồn khác, tạo CheckoutService sẽ thất bại vì dependency thiếu. Phải thiết kế consumer optional/conditional tương ứng.

### Bài 5 — Một context, hai modules khác nhau

Selector chỉ import StandardShippingConfig, nhưng `AppConfig` cũng `@Import(ExpressShippingConfig.class)`. Có bảo đảm đúng một Carrier không?

**Đáp án:** Không. Import khác vẫn bổ sung bean definitions. Hai configs có thể có bean names trùng hoặc cùng type, dẫn tới conflict/ambiguity tùy thiết kế.

### Bài 6 — ImportSelector hay @Profile?

Một ứng dụng có hai cấu hình `DevConfig` và `ProdConfig` chỉ khác môi trường dev/prod, không có custom annotation attributes. Có bắt buộc tự viết ImportSelector không?

**Đáp án:** Không. `@Profile` hoặc conditional configuration có thể đơn giản hơn. Dùng ImportSelector khi cần lựa chọn class dựa trên logic/metadata mang tính extension.

### Bài 7 — Metadata

`metadata.getClassName()` bên trong ShippingSelector trả tên `ExpressShippingConfig` có đúng không?

**Đáp án:** Không nhất thiết, và không phải semantics của method này. Nó mô tả **importing class**, ví dụ `ExpressAppConfig`; class được chọn nằm trong mảng trả về từ `selectImports`.

### Bài 8 — Static inner config

Trong ví dụ mục 8, `StandardShippingConfig` là nested class. Tại sao dùng `StandardShippingConfig.class.getName()` tốt hơn tự gõ `"ImportSelectorDemo.StandardShippingConfig"`?

**Đáp án:** Vì JVM binary name của nested class chứa `$`; `Class.getName()` trả đúng định danh để Spring load, tránh sai class name khi refactor.

### Bài 9 — DeferredImportSelector

Có cần `DeferredImportSelector` để bắt Spring đợi đến lần HTTP request đầu tiên mới quyết định provider không?

**Đáp án:** Không. Deferred chỉ trì hoãn giai đoạn xử lý imports trong bootstrap; nó không chọn bean động theo request. Runtime switching cần một abstraction/service routing khác.

### Bài 10 — EnvironmentAware

`ShippingSelector` implement `EnvironmentAware`. Khi nào Spring gọi `setEnvironment(...)` so với `selectImports(...)`?

**Đáp án:** Theo contract, Aware callback được thực hiện trước khi selector đưa ra lựa chọn. Nhờ đó selector có thể đọc property trong `Environment`.

### Bài 11 — Chọn nhiều configurations

Có được trả mảng gồm `MetricsConfig.class.getName()` và `ShippingConfig.class.getName()` không?

**Đáp án:** Có, miễn cả hai class tồn tại và không gây xung đột bean definitions ngoài ý muốn. `ImportSelector` không bị giới hạn một class; ví dụ shipping chỉ chọn một để bảo đảm exactly one Carrier.

### Bài 12 — Kiểm thử hai chế độ trong cùng container

Một test tạo context với STANDARD, gọi `context.getEnvironment().getPropertySources().addFirst(...)` để đổi EXPRESS **sau refresh** rồi kiểm tra Carrier. Có chắc Spring tự thay Carrier hiện có không?

**Đáp án:** Không. Import selection đã diễn ra trong configuration processing; thay property sau refresh không tự cập nhật import graph. Tạo context khác nếu muốn so sánh behavior bootstrap.

## 13. Checklist thiết kế một ImportSelector

- Annotation/`@Import` đặt trên class được Spring biết đến chưa?
- `selectImports` trả **tên class đầy đủ** và mảng rỗng khi không cần nhập, không trả bean name?
- Input lựa chọn đến từ metadata/`Environment` rõ ràng, đã khai báo chính sách ưu tiên chưa?
- Selector có tránh side effects, I/O và eager lookup vào business beans không?
- Mọi classes có thể được chọn đều có trên runtime classpath hoặc được kiểm tra trước đúng cách chưa?
- Có đảm bảo không vô tình nhập hai configuration implementations cho một dependency đơn?
- Các beans được import có constructor/factory dependencies và qualifiers phù hợp không?
- Conditional/profile trên imported configs có thể làm một bean biến mất không?
- Đã phân biệt normal `ImportSelector` với `DeferredImportSelector` và registrar chưa?
- Có integration test cho mỗi nhánh và một test cho giá trị cấu hình bất hợp lệ chưa?
- Thay property sau context refresh có bị nhầm là cơ chế reload cấu hình không?
- Đã kiểm tra bean definitions của context thật thay vì chỉ unit test selector method chưa?

## 14. Tổng kết

`ImportSelector` cho phép **chọn danh sách class cần đưa vào Spring configuration graph** dựa trên `AnnotationMetadata` và những dữ kiện hạ tầng như `Environment`. Phương thức `selectImports(...)` trả **mảng tên class đầy đủ**, không trả bean instances và không trả bean names. Spring sau đó xử lý các class được chọn để đăng ký bean definitions và tạo managed beans theo dependency graph/scope/lifecycle bình thường.

Dùng `@Import(Selector.class)` trực tiếp hoặc bọc trong một custom `@Enable...` annotation sẽ tạo được API cấu hình module rõ ràng. `@Conditional`/`@Profile` thích hợp cho điều kiện bật/tắt đơn giản; `DeferredImportSelector` phục vụ lựa chọn ở giai đoạn parsing muộn hơn, không phải thay cấu hình theo request. **Đưa ra quyết định tại bootstrap** khác với **runtime routing**: nếu cần thay provider khi ứng dụng đang chạy, thiết kế một service abstraction có chính sách thay đổi rõ ràng thay vì mong selector tự chạy lại.

### Tài liệu chính thức

- [Spring Framework 6.2 — `ImportSelector` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/context/annotation/ImportSelector.html).
- [Spring Framework 6.2 — `DeferredImportSelector` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/context/annotation/DeferredImportSelector.html).
- [Spring Framework 6.2 — `@Import` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/context/annotation/Import.html).
- [Spring Framework 6.2 — `ImportBeanDefinitionRegistrar` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/context/annotation/ImportBeanDefinitionRegistrar.html).
- [Spring Framework 6.2 — Composing Java-based Configurations](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/composing-configuration-classes.html).
- [Spring Framework 6.2 — Conditional Bean Definitions](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/composing-configuration-classes.html#beans-java-conditional).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="configuration-composition-006">Thời điểm xử lý DeferredImportSelector</a>

<details>
<summary>Xem chi tiết</summary>

# Thời điểm xử lý DeferredImportSelector trong Spring Core Container

## 1. Tổng quan

Khi sử dụng Java configuration, Spring có thể ghép các lớp cấu hình thông qua `@Import`. Ta có thể import class trực tiếp, nhờ `ImportSelector` chọn các class cần import, hoặc dùng `DeferredImportSelector` để **trì hoãn bước chọn imports** đến sau khi các class cấu hình thông thường được Spring phân tích.

Điểm dễ hiểu sai là chữ **deferred**. Ở đây, nó có nghĩa **xử lý muộn trong pha phân tích metadata của configuration**, không phải **tạo bean sau khi ứng dụng khởi động**, không phải **chạy trên thread nền**, cũng không phải **bật lazy initialization**.

Theo hợp đồng Javadoc của Spring, `DeferredImportSelector` là một dạng `ImportSelector` được xử lý sau các `@Configuration` classes trong quá trình configuration processing. Đây là nền tảng hữu ích cho những imports phụ thuộc vào điều kiện, nhóm lựa chọn hoặc chiến lược auto-configuration.

### Mục tiêu

Sau bài học, bạn sẽ:

1. Nắm rõ điểm khác biệt giữa `ImportSelector` và `DeferredImportSelector`.
2. Đọc đúng timeline parser → deferred imports → bean definitions → bean instances.
3. Hiểu `AnnotationMetadata`, `getImportGroup()`, `Group.process()`, `Group.selectImports()` và `Group.Entry`.
4. Biết tác động và giới hạn của `Ordered`/`@Order` đối với deferred selectors.
5. Tránh dựa vào trạng thái bean runtime hoặc bean-definition registry chưa hoàn chỉnh trong thời điểm selector chạy.
6. Chạy một ví dụ Java 17+ có normal selector, nested normal selector và deferred group, đo thứ tự callbacks.
7. Hiểu vì sao Spring Boot dùng `AutoConfigurationImportSelector`, nhưng không nhầm API Boot với Spring Core.
8. Chẩn đoán lỗi khi một cấu hình được chọn bởi deferred selector vẫn không xuất hiện thành bean.

## 2. Những gì @Import có thể nhận

`@Import` hỗ trợ nhiều cách bổ sung nguồn cấu hình. Trường hợp tường minh nhất:

```java
@Configuration
@Import({StorageConfiguration.class, PricingConfiguration.class})
public class ApplicationConfiguration {
}
```

Đây là đoạn cấu hình minh họa, giả định hai class `StorageConfiguration` và `PricingConfiguration` có thật trong classpath và đã import `Configuration`, `Import`. Khi `ApplicationConfiguration` được nạp, Spring phân tích `@Import` rồi đưa các cấu hình liên quan vào cùng ApplicationContext.

Ngoài việc nhập class trực tiếp, `@Import` có thể làm việc với:

| Loại | Ý nghĩa |
| --- | --- |
| `ImportSelector` | Chọn tên class/configuration cần import trong quá trình xử lý thông thường. |
| `DeferredImportSelector` | Chọn tên class/configuration tại pha deferred của parser. |
| `ImportBeanDefinitionRegistrar` | Can thiệp vào registry để đăng ký bean definitions bằng code. |
| Class cấu hình hoặc component | Được thêm trực tiếp vào luồng xử lý cấu hình. |

`DeferredImportSelector` **kế thừa `ImportSelector`**, vì vậy nó vẫn sử dụng:

```java
String[] selectImports(AnnotationMetadata importingClassMetadata);
```

Kết quả là **mảng tên class đầy đủ**, chẳng hạn `com.example.feature.FeatureConfiguration`, không phải bean name hay object instance. Nếu class là nested class Java, `SomeNestedConfig.class.getName()` trả binary name hợp lệ; nên dùng API này thay vì tự viết chuỗi.

## 3. Normal selector và deferred selector

Một normal selector:

```java
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;

public final class NormalFeatureSelector implements ImportSelector {
    @Override
    public String[] selectImports(AnnotationMetadata metadata) {
        return new String[] {
            NormalFeatureConfiguration.class.getName()
        };
    }
}
```

Đây là class minh họa, cần class `NormalFeatureConfiguration` cụ thể. Khi parser gặp nó trong `@Import`, Spring xử lý selector trong luồng phân tích imports thông thường và có thể tiếp tục parse configuration được trả về.

Một deferred selector:

```java
import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.core.type.AnnotationMetadata;

public final class DeferredFeatureSelector
        implements DeferredImportSelector {

    @Override
    public String[] selectImports(AnnotationMetadata metadata) {
        return new String[] {
            DeferredFeatureConfiguration.class.getName()
        };
    }
}
```

Đây cũng là class minh họa cần `DeferredFeatureConfiguration`. Khác biệt không nằm ở chữ ký method mà ở **thời điểm `ConfigurationClassParser` gọi/điều phối nó**.

| Thuộc tính | ImportSelector | DeferredImportSelector |
| --- | --- | --- |
| Kế thừa | Interface gốc | Mở rộng ImportSelector |
| Kết quả cơ bản | `String[]` class names | `String[]` class names |
| Thời điểm xử lý | Khi parser xử lý normal imports | Sau lượt parsing các configuration thông thường |
| Group API | Không có interface Group tương ứng | `DeferredImportSelector.Group` |
| Điều khiển singleton creation | Không | Không |
| Hỗ trợ điều kiện khi import | Có thể | Có thể; thường hữu ích hơn khi cần xử lý muộn |
| Dùng cho Spring Boot auto-configuration | Không phải cơ chế chính của Boot auto-configuration | Có: `AutoConfigurationImportSelector` implements DeferredImportSelector |

Dù dùng selector nào, **Spring vẫn cần tạo bean definitions và quản lý bean lifecycle sau đó**.

## 4. Chính xác deferred xảy ra ở đâu trong refresh?

Một `AnnotationConfigApplicationContext` có chu trình `refresh()`. Trong phần xử lý BeanFactoryPostProcessors, `ConfigurationClassPostProcessor` phân tích các configuration classes và phối hợp `ConfigurationClassParser` với việc đăng ký metadata.

Sơ đồ khái niệm:

```text
new AnnotationConfigApplicationContext(RootConfig.class)
                         |
                         v
                      refresh()
                         |
            ConfigurationClassPostProcessor
                         |
                         v
               ConfigurationClassParser
                         |
           +-------------+-------------+
           |                           |
   @Import(Config.class)       @Import(ImportSelector.class)
           |                           |
           +---------- normal processing
                         |
                @Import(DeferredImportSelector.class)
                         |
                    thu gom selector
                         |
                 parse xong các cấu hình thường
                         |
                   deferred handler
                         |
                 group/order/filter imports
                         |
                 parse các deferred imports
                         |
                         v
                bean-definition reader
                         |
                 registry có bean definitions
                         |
          register BeanPostProcessors / tạo singletons
                         |
                    refresh hoàn tất
```

**Điểm quan trọng:** khái niệm “sau tất cả `@Configuration` được xử lý” ở tài liệu API nói về **configuration parsing**, không có nghĩa Spring đã gọi tất cả constructor hoặc `@Bean` factory methods.

Có thể phân ra bốn thời điểm:

1. **Parse source metadata:** tìm config, annotation và import candidates.
2. **Choose imports:** normal selectors được xử lý trong parse; deferred selectors được chọn ở giai đoạn deferred.
3. **Register bean definitions:** đưa metadata bean cuối cùng vào BeanFactory/registry.
4. **Instantiate bean:** gọi constructor/factory methods theo scope và dependency, chạy post-processors/lifecycle.

Chính pha thứ **hai** được trì hoãn bởi `DeferredImportSelector`.

### Tại sao không thể xem deferred là lazy?

`@Lazy` ảnh hưởng thời điểm bean singleton được khởi tạo (trừ khi dependency khác khiến phải tạo sớm). `DeferredImportSelector` ảnh hưởng thời điểm Spring **quyết định class cấu hình nào được nhập**. Một cấu hình được deferred import vẫn có thể khai báo singleton eager được khởi tạo trước khi `refresh()` hoàn thành.

Ví dụ một deferred selector chọn cấu hình có bean `HttpClient`. Nếu bean đó không lazy và là singleton bình thường, Spring vẫn có thể khởi tạo HTTP client trong startup. Không được dùng deferred selector để bảo đảm client chỉ mở kết nối tại request đầu tiên.

## 5. Parser thu gom deferred imports ra sao?

Về mặt implementation, `ConfigurationClassParser` gặp một implementation `DeferredImportSelector` và **giữ nó cùng metadata của class đã import selector**, thay vì xử lý ngay như normal `ImportSelector`.

Cuối lượt parse, deferred selectors được gom và xử lý, có thể sắp theo thứ tự `Ordered` và gom vào các groups. Imports mà Group trả về tiếp tục được parser xử lý như những configuration candidates.

Quá trình này có thể mở ra imports mới, trong đó có imports lồng nhau. Vì vậy sơ đồ một vòng là mô hình học tập, **không phải hợp đồng rằng parser chỉ chạy đúng một pass**.

### Không suy luận quá mức về BeanFactory

Ngay trong callback `DeferredImportSelector.selectImports`, việc `ConfigurationClassParser` đã biết một `@Bean` method không nhất thiết có nghĩa bean definition của method ấy đã được BeanDefinitionReader đăng ký vào registry.

Do đó, một selector tự viết như sau là thiết kế dễ gây lỗi:

```java
// Phản ví dụ mang tính khái niệm:
// Trong selectImports(), không gọi beanFactory.getBean("appService")
// để kiểm tra xem một bean nghiệp vụ đã tồn tại hay chưa.
```

Đặc biệt không gọi `getBean()` để ép tạo bean nghiệp vụ trong lúc parsing; điều này có thể đưa bean creation vào pha sớm trước khi processor/lifecycle sẵn sàng.

Thay vào đó, ưu tiên metadata importer, `Environment` hoặc cơ chế condition có hợp đồng phase rõ ràng.

## 6. AnnotationMetadata thuộc class nào?

Phương thức:

```java
String[] selectImports(AnnotationMetadata importingClassMetadata)
```

nhận metadata của **class đang thực hiện import**, không tự động là metadata của class sẽ được import.

Giả sử cấu hình:

```java
@Configuration
@Import(DeferredFeatureSelector.class)
class PaymentApplicationConfiguration {
}
```

Khi selector được xử lý, metadata mà nó nhận tương ứng `PaymentApplicationConfiguration`. Selector có thể đọc annotations hoặc meta-annotations được khai báo trên cấu hình ấy.

Nếu tạo một annotation `@EnableFeature` có attributes rồi meta-annotate bằng `@Import`, selector có thể đọc attributes từ metadata của configuration class đã dùng annotation đó. Khi cần hỗ trợ meta-annotation và alias, nên sử dụng metadata/MergedAnnotations API của Spring thay vì reflection trực tiếp trên các bean instances chưa được tạo.

Điều này liên hệ mật thiết với `Group.Entry`: entry không chỉ mang tên target class, mà còn mang metadata của importer.

## 7. DeferredImportSelector.Group là gì?

Một deferred selector có thể override:

```java
Class<? extends DeferredImportSelector.Group> getImportGroup();
```

Mặc định không cung cấp group class riêng (giá trị mặc định có thể là `null`). Nếu selector trả một class Group, Spring dùng group đó để **thu thập nhiều imports và quyết định kết quả cuối cùng**.

Interface Group có:

```java
void process(
        AnnotationMetadata metadata,
        DeferredImportSelector selector);

Iterable<DeferredImportSelector.Group.Entry> selectImports();
```

**`Group.process`** nhận importing metadata và selector tương ứng. Group có thể tự gọi `selector.selectImports(metadata)`, ghi nhận candidates, deduplicate hoặc áp dụng policy.

**`Group.selectImports`** trả những `Entry` mà parser sẽ tiếp tục nhập. Mỗi Entry chứa metadata của importing configuration và tên class import.

Sơ đồ:

```text
Selector A --> Group.process(metadataA, selectorA) --+
                                                       |
Selector B --> Group.process(metadataB, selectorB) --+--> Group state
                                                       |
Selector C --> Group.process(metadataC, selectorC) --+
                                                       |
                                                      v
                                              Group.selectImports()
                                                      |
                                                      v
                                             Iterable<Group.Entry>
                                                      |
                                                      v
                                            Parser xử lý config imports
```

**Một điểm hay bị nhầm:** khi dùng Group, Spring không nhất thiết gọi selector `selectImports` theo một bước tách biệt trước Group. Chính implementation `Group.process` quyết định có gọi phương thức đó hay không. Nếu custom group bỏ qua việc gọi selector, các side effects đặt trong `selector.selectImports` sẽ không xảy ra. Không nên đặt business side effects ở bất kỳ selector callback nào.

### Vì sao Group có giá trị?

Khi nhiều modules cùng đóng góp imports, Group có thể:

- Thu thập imports của nhiều selectors.
- Loại trùng class names theo quy tắc được chỉ định.
- Áp exclusions.
- Sắp xếp thứ tự xử lý class imports.
- Quyết định ưu tiên giữa nhiều tập ứng viên.

Tuy nhiên, **loại trùng imports không đồng nghĩa loại trùng bean definitions của hai config khác nhau**. Các xung đột bean names/types vẫn phải được xử lý tại tầng registration/DI.

## 8. Ordered và @Order

`DeferredImportSelector` có thể implement `Ordered` hoặc sử dụng `@Order` để đưa ra precedence tương đối giữa các deferred selectors. Với `Ordered`, giá trị số nhỏ hơn thường có độ ưu tiên cao hơn.

Nhưng **đừng đánh đồng**:

- Selector có order thấp với target bean được tạo trước.
- Import được parse trước với bean chắc chắn xuất hiện trước trong singleton cache.
- Ordering selector với `@DependsOn` của bean.
- Ordering trong Spring Boot auto-configuration với global ordering của mọi custom selector.

Order ở đây phục vụ **pha selection**. Khi có Group, policy Group và ordering của các groups/imports còn ảnh hưởng cách kết quả cuối được đưa vào parser. Nếu yêu cầu application đảm bảo dependency, phải thể hiện dependency qua constructor/method parameters, `@DependsOn` khi đúng nghĩa, hoặc giải pháp lifecycle thích hợp.

## 9. Ví dụ đầy đủ Java 17+: đo thứ tự callback

Chúng ta sử dụng Spring Framework **6.2.19** và `AnnotationConfigApplicationContext`, không cần Boot. Bài thực hành có:

- `ImmediateSelector` — một normal `ImportSelector`.
- `ImmediateConfiguration` — class mà normal selector chọn.
- `NestedImmediateSelector` — normal selector được import **bên trong** `ImmediateConfiguration`.
- `DeferredSelector` — `DeferredImportSelector`.
- `DeferredGroup` — Group nhận selector, gọi `selectImports`, trả `Entry`.
- `DeferredConfiguration` — class được chọn ở pha deferred.
- Một danh sách ghi sự kiện. Sau khi context refresh, chương trình kiểm chứng **thứ tự selector callbacks** chứ không dùng thứ tự tạo bean làm đại diện.

### 9.1. Cấu trúc dự án

```text
deferred-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/DeferredDemo.java
```

**`settings.gradle`**

```groovy
rootProject.name = 'deferred-demo'
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
    mainClass = 'demo.DeferredDemo'
}
```

### 9.2. Java source

**`src/main/java/demo/DeferredDemo.java`**

```java
package demo;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;

public final class DeferredDemo {
    private static final List<String> EVENTS = new ArrayList<>();

    @Configuration(proxyBeanMethods = false)
    @Import({ImmediateSelector.class, DeferredSelector.class})
    public static class RootConfig {
    }

    public static class ImmediateSelector implements ImportSelector {
        @Override
        public String[] selectImports(AnnotationMetadata metadata) {
            EVENTS.add("immediate");
            return new String[] { ImmediateConfig.class.getName() };
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(NestedSelector.class)
    public static class ImmediateConfig {
        @Bean
        String immediateMessage() {
            return "immediate";
        }
    }

    public static class NestedSelector implements ImportSelector {
        @Override
        public String[] selectImports(AnnotationMetadata metadata) {
            EVENTS.add("nested");
            return new String[0];
        }
    }

    public static class DeferredSelector implements DeferredImportSelector {
        @Override
        public Class<? extends Group> getImportGroup() {
            return DeferredGroup.class;
        }

        @Override
        public String[] selectImports(AnnotationMetadata metadata) {
            EVENTS.add("deferred.select");
            return new String[] { DeferredConfig.class.getName() };
        }
    }

    public static class DeferredGroup
            implements DeferredImportSelector.Group {

        private final List<Entry> entries = new ArrayList<>();

        @Override
        public void process(
                AnnotationMetadata metadata,
                DeferredImportSelector selector) {
            EVENTS.add("deferred.process");
            for (String name : selector.selectImports(metadata)) {
                entries.add(new Entry(metadata, name));
            }
        }

        @Override
        public Iterable<Entry> selectImports() {
            EVENTS.add("deferred.group.result");
            return List.copyOf(entries);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class DeferredConfig {
        @Bean
        String deferredMessage() {
            return "deferred";
        }
    }

    public static void main(String[] args) {
        EVENTS.clear();

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(RootConfig.class)) {

            int immediate = EVENTS.indexOf("immediate");
            int nested = EVENTS.indexOf("nested");
            int process = EVENTS.indexOf("deferred.process");
            int select = EVENTS.indexOf("deferred.select");
            int result = EVENTS.indexOf("deferred.group.result");

            System.out.println("immediate-before-deferred="
                    + (immediate >= 0 && immediate < process));

            System.out.println("nested-before-deferred="
                    + (nested >= 0 && nested < process));

            System.out.println("group-calls-selector="
                    + (process >= 0 && process < select));

            System.out.println("selector-before-group-result="
                    + (select >= 0 && select < result));

            System.out.println("immediate-bean="
                    + context.getBean("immediateMessage", String.class));

            System.out.println("deferred-bean="
                    + context.getBean("deferredMessage", String.class));

            System.out.println("event-count=" + EVENTS.size());
        }
    }
}
```

Tất cả class đều thuộc một source file, nên bạn có thể copy nội dung vào đúng vị trí và chạy. `RootConfig`, `ImmediateConfig` và `DeferredConfig` là public static nested classes; Spring có thể import chúng qua tên class thực lấy từ `Class.getName()`. Không tạo riêng file cho các nested classes.

### 9.3. Chạy

```bash
gradle run --quiet
```

Nếu có Gradle Wrapper, dùng `./gradlew run --quiet` trên Linux/macOS hoặc `gradlew.bat run --quiet` trên Windows. Chỉ gọi `javac DeferredDemo.java` riêng không đủ nếu không cung cấp Spring jars vào classpath.

**Stdout dự kiến từ các println:**

```text
immediate-before-deferred=true
nested-before-deferred=true
group-calls-selector=true
selector-before-group-result=true
immediate-bean=immediate
deferred-bean=deferred
event-count=5
```

Log của Gradle/runtime nếu xuất hiện không phải một phần của các dòng `System.out.println`.

### 9.4. Giải thích kết quả

1. Spring nạp `RootConfig` và gặp `@Import` gồm một normal selector và một deferred selector.
2. `ImmediateSelector` được xử lý trong luồng normal import, ghi `immediate` và chọn `ImmediateConfig`.
3. `ImmediateConfig` có `@Import(NestedSelector.class)`; nested normal selector ghi `nested`.
4. `DeferredSelector` được thu gom; sau normal parsing, framework chuyển nó tới `DeferredGroup`.
5. `Group.process` ghi `deferred.process` rồi **chính Group** gọi `DeferredSelector.selectImports`, sinh `deferred.select`.
6. `Group.selectImports` ghi `deferred.group.result` rồi trả `Entry` chỉ tới `DeferredConfig`.
7. Parser tiếp tục đưa `DeferredConfig` vào tập configuration metadata, và bean `deferredMessage` được tạo theo lifecycle thông thường.
8. Mỗi selector callback trong fixture chạy đúng một lần, nên có năm events.

Các boolean quan sát **quan hệ thứ tự giữa các callbacks**. Ngược lại, `immediate-bean` và `deferred-bean` chứng minh cuối cùng hai imports đều tạo được beans trong cùng context. **Không** suy luận bean nào được instantiate trước từ thứ tự lựa chọn imports.

## 10. Ví dụ Group loại trùng imports

Khi có nhiều deferred selectors cùng Group, có thể muốn các config class names chỉ xuất hiện một lần trong danh sách Group trả lại:

```java
public final class UniqueImportsGroup
        implements DeferredImportSelector.Group {

    private final java.util.Map<String, Entry> selected =
            new java.util.LinkedHashMap<>();

    @Override
    public void process(
            AnnotationMetadata metadata,
            DeferredImportSelector selector) {

        for (String className : selector.selectImports(metadata)) {
            selected.putIfAbsent(
                    className,
                    new Entry(metadata, className)
            );
        }
    }

    @Override
    public Iterable<Entry> selectImports() {
        return selected.values();
    }
}
```

Đây là một class demo riêng, yêu cầu imports `DeferredImportSelector` và `AnnotationMetadata`. `LinkedHashMap` giữ entry đầu tiên theo tên class. Với những ứng dụng yêu cầu xét annotation metadata khác nhau của nhiều importers, giữ entry đầu tiên **có thể không đúng policy**: cần quyết định dựa vào hợp đồng nghiệp vụ.

### Một bẫy quan trọng

Group loại tên config trùng **không loại được** việc hai config *khác nhau* cùng định nghĩa `@Bean("httpClient")`. Xung đột bean name, hai candidate cùng type, bean definition overriding policy và injection ambiguity vẫn tồn tại ở pha registry/DI.

## 11. Liên hệ với @Conditional

Một số imports chỉ phù hợp khi cấu hình đáp ứng điều kiện. `DeferredImportSelector` hữu ích vì những configuration thông thường đã được parser xử lý trước, từ đó có thêm ngữ cảnh cho một số chiến lược chọn import.

Nhưng không nên đồng nhất “đã xử lý `@Configuration` metadata” với “mọi bean đã tồn tại trong BeanFactory”. Conditions và registration có các pha khác nhau. Một điều kiện đọc `Environment`/classpath có thể an toàn; một điều kiện phụ thuộc `getBean()` hoặc trạng thái BeanFactory chưa hoàn thiện dễ sai.

### Selector theo Environment

```java
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;

public final class AuditFeatureSelector
        implements DeferredImportSelector, EnvironmentAware {

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public String[] selectImports(AnnotationMetadata metadata) {
        boolean enabled = environment.getProperty(
                "feature.audit.enabled", Boolean.class, false);

        return enabled
                ? new String[] { AuditConfiguration.class.getName() }
                : new String[0];
    }
}
```

Đoạn này là snippet riêng, cần class `AuditConfiguration` trong ứng dụng. Spring cung cấp `Environment` qua `EnvironmentAware` trong quá trình chuẩn bị selector. Điều kiện đọc property không cần instantiate service nghiệp vụ.

Dù vậy, chỉ kiểm tra property **không bắt buộc** phải dùng deferred processing. Nếu không cần Group/timing, normal `ImportSelector` hoặc `@Conditional` trực tiếp trên cấu hình thường đơn giản hơn.

## 12. Vai trò của Spring Boot auto-configuration

Một trường hợp thực tế nổi tiếng là `AutoConfigurationImportSelector` của **Spring Boot**: nó implement `DeferredImportSelector` để phục vụ auto-configuration.

Tư duy của Boot là application configuration được xử lý trước, rồi Boot chọn các default/auto-configurations thích hợp theo classpath, property, điều kiện loại trừ và metadata ordering. Các cơ chế như `@ConditionalOnMissingBean` có thể giúp default bean lùi lại trước bean do ứng dụng khai báo trong điều kiện hợp lệ.

Cần ba phân biệt:

1. `AutoConfigurationImportSelector` thuộc **Spring Boot**, không nằm trong dependency `spring-context` thuần của demo.
2. `@ConditionalOnMissingBean` là annotation **Spring Boot**, không phải Spring Core `@Conditional`.
3. Boot có logic candidate discovery, ordering, filtering và condition evaluation riêng; bản thân `DeferredImportSelector` **không tự triển khai những policy ấy**.

**Không được suy ra** một custom deferred selector tự biết toàn bộ bean definitions đã đăng ký hoặc có khả năng “đợi tới cuối” rồi tránh mọi xung đột.

## 13. DeferredImportSelector khác các cơ chế thời gian khác

| Công cụ | Thời điểm hoặc trách nhiệm chính |
| --- | --- |
| `ImportSelector` | Chọn imports trong parsing thông thường |
| `DeferredImportSelector` | Chọn imports muộn hơn trong parsing cấu hình |
| `DeferredImportSelector.Group` | Tổng hợp/lọc/sắp imports của selectors |
| `ImportBeanDefinitionRegistrar` | Đăng ký BeanDefinitions bằng API |
| `BeanFactoryPostProcessor` | Điều chỉnh metadata/definitions trước tạo beans thường |
| `BeanPostProcessor` | Xử lý bean instances trong lifecycle |
| `@Lazy` | Trì hoãn khởi tạo bean singleton khi thích hợp |
| `ObjectProvider<T>` | Deferred dependency lookup lúc caller yêu cầu |
| `@DependsOn` | Ràng buộc khởi tạo phụ thuộc theo bean definition |
| `@Async` | Chạy lời gọi method theo async infrastructure |
| `ContextRefreshedEvent` | Sự kiện context đã refresh |
| Boot auto-configuration | Lựa chọn/đăng ký defaults theo Boot conditions |

**Deferred selector không phải scheduler, async executor hoặc lifecycle callback sau startup.**

## 14. Lỗi thường gặp và cách chẩn đoán

### Lỗi 1 — Tưởng bean của deferred import chỉ được tạo sau refresh

Deferred imports vẫn được xử lý trong quá trình configuration processing của refresh. Bean singleton eager có thể được tạo trước khi refresh hoàn tất.

### Lỗi 2 — Sử dụng getBean trong selector

`getBean()` có thể kích hoạt tạo object quá sớm trước khi infrastructure sẵn sàng, hoặc nhìn thấy trạng thái chưa đầy đủ. Ưu tiên metadata/Environment và đúng extension point.

### Lỗi 3 — Tưởng BeanFactory chứa đầy đủ definitions

Khi deferred selector được gọi, parser có thể đã phát hiện `@Bean` methods nhưng reader chưa hoàn tất đăng ký. `containsBeanDefinition(...)` lúc đó không thể mặc định đại diện cho registry cuối cùng.

### Lỗi 4 — selectImports trả bean name thay vì class name

`selectImports` trả fully qualified **class names**. Hãy sử dụng `TargetConfig.class.getName()`. Với nested class Java, cách này tránh lỗi binary name.

### Lỗi 5 — Group bỏ quên gọi selector.selectImports

Custom `Group.process` chịu trách nhiệm thu thập kết quả theo hợp đồng của nó. Nếu group không gọi selector và không tự tạo entries, imports có thể rỗng dù selector có logic trong `selectImports`.

### Lỗi 6 — Trả String[] từ Group.selectImports

Group phải trả `Iterable<Group.Entry>`, không phải `String[]`. Entry chứa metadata importer và tên class target.

### Lỗi 7 — Dùng @Order để ép bean instantiation order

`Ordered`/`@Order` của selector điều khiển precedence selection, không ràng buộc thứ tự constructor/factory method của các singleton. Dùng dependency injection hoặc lifecycle mechanism đúng ý định.

### Lỗi 8 — Không nạp root configuration

Nếu class có `@Import(MySelector.class)` không được register/import/scan vào context, selector sẽ không chạy. Kiểm tra composition root và annotations.

### Lỗi 9 — Tưởng Group dedup ngăn trùng bean types

Hai configuration classes khác nhau vẫn có thể tạo trùng bean names hoặc cùng return interface. Kiểm tra bean overriding, `@Primary` và `@Qualifier`.

### Lỗi 10 — Nhầm conditional Boot với conditional Core

`@ConditionalOnMissingBean` là Boot API. Project chỉ có Spring Framework dependency không có annotation đó. Các conditions phụ thuộc beans phải được đánh giá đúng phase.

### Lỗi 11 — Dùng DeferredImportSelector cho hot plugin loading

Deferred imports được quyết định khi context được dựng; không tự thêm plugin vào registry sau khi ứng dụng đã chạy.

### Lỗi 12 — Tạo selector có side effects nặng

Gọi network, DB, mở connection pool hoặc ghi dữ liệu trong `selectImports` làm startup chậm và khiến context khó test. Selector nên thuần metadata càng nhiều càng tốt.

### Lỗi 13 — Test bằng constructor order

Nếu muốn đo timing của selector, ghi event ngay trong `selectImports` hoặc Group. Constructors/`@PostConstruct` chạy pha khác và phụ thuộc bean graph.

### Lỗi 14 — Tin rằng deferred chỉ có đúng một vòng xử lý

Imported configurations có thể có imports lồng nhau và dẫn đến candidates mới. Xem deferred như một pha của parser có khả năng xử lý thêm metadata, không như một danh sách bất biến chạy một lần trong mọi trường hợp.

## 15. Phương pháp kiểm thử hiệu quả

Test nên tách thành hai lớp:

**Kiểm thử selection phase:** tạo context chỉ gồm configuration và selectors cần nghiên cứu, ghi events tại callback, assert normal selector xử lý trước deferred group khi fixture áp dụng. Nếu nhiều Group hoặc `Ordered`, kiểm tra đúng quan hệ thứ tự mà policy yêu cầu.

**Kiểm thử kết quả registration:** sau `refresh()`, dùng `containsBean`, `getBean` hoặc `getBeanNamesForType` để xác nhận bean được chọn. Nếu class được selector chọn nhưng `@Conditional` loại bỏ, kiểm tra điều kiện thay vì kết luận selector không chạy.

Một snippet lấy bean từ demo:

```java
try (AnnotationConfigApplicationContext context =
         new AnnotationConfigApplicationContext(
                 DeferredDemo.RootConfig.class)) {

    System.out.println(
            context.containsBean("deferredMessage"));
}
```

Đoạn này có thể biên dịch khi dùng package demo và import `AnnotationConfigApplicationContext` của ví dụ, kết quả `true`. Nhưng nó **chỉ** kiểm tra bean tồn tại sau refresh; recorder events mới là bằng chứng về timing.

### Thử nghiệm biến thể hữu ích

- Đổi `DeferredSelector` thành normal `ImportSelector`: group callback sẽ không còn được gọi.
- Giữ deferred selector nhưng bỏ override `getImportGroup()`: Spring vẫn xử lý deferred, chỉ không dùng custom group.
- Tạo hai deferred selectors cùng trả một Group class: kiểm tra `Group.process` nhận đủ metadata của các importers.
- Dùng `Ordered` với precedence khác nhau: ghi order của callback trong một test riêng, không suy ra thứ tự tạo beans.
- Thêm `@Lazy` cho bean trong deferred config: timing tạo bean thay đổi, nhưng **selector vẫn chạy trong parsing**.
- Thêm một `@Conditional` dựa trên Environment trên imported configuration: theo dõi vừa callback vừa kết quả đăng ký bean.

## 16. Câu hỏi tự luyện

**Câu 1.** Sự khác biệt quan trọng nhất giữa `ImportSelector` và `DeferredImportSelector` là gì?

**Đáp:** Thời điểm parser chọn imports: deferred selector được thu gom và xử lý sau normal configuration parsing của lượt tương ứng.

**Câu 2.** Deferred import có bảo đảm bean được instantiate sau khi context hoàn tất startup không?

**Đáp:** Không. Eager singleton có thể được instantiate trong cùng `refresh()`.

**Câu 3.** `selectImports` nhận annotation metadata của class nào?

**Đáp:** Configuration class đã thực hiện import selector.

**Câu 4.** Một selector trả `"paymentClient"` thay vì tên configuration class đầy đủ có đúng không?

**Đáp:** Không. `selectImports` phải trả class names hợp lệ.

**Câu 5.** Nếu một Group không gọi `selector.selectImports(...)`, Spring có tự động gọi hộ trước khi Group.process không?

**Đáp:** Không nên giả định. Group là nơi điều phối cách xử lý; nó phải tạo các `Entry` cuối cùng theo logic đã thiết kế.

**Câu 6.** `Group.Entry` dùng để làm gì?

**Đáp:** Giữ annotation metadata của importer cùng class name được chọn, để parser xử lý đúng ngữ cảnh.

**Câu 7.** `@Order` trên deferred selector có buộc bean của selector đó được khởi tạo trước không?

**Đáp:** Không. Order thuộc pha selection, không bảo đảm constructor ordering.

**Câu 8.** Vì sao Spring Boot dùng `AutoConfigurationImportSelector`?

**Đáp:** Để chọn tập auto-configurations trong pha deferred, kết hợp với cơ chế filtering/ordering/conditional của Boot.

**Câu 9.** Làm thế nào để kiểm chứng selector thật sự chạy muộn?

**Đáp:** Ghi lại events trong normal selector và deferred selector/Group, rồi so sánh vị trí các callbacks trong integration test.

**Câu 10.** Muốn trì hoãn một client kết nối đến khi thật sự dùng, nên làm gì?

**Đáp:** Dùng `@Lazy` hoặc cơ chế lookup phù hợp, thiết kế lifecycle và connection policy của client; không dùng DeferredImportSelector thay thế.

## 17. Tài liệu chính thức

- [Spring Framework DeferredImportSelector API](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/context/annotation/DeferredImportSelector.html): hợp đồng chạy deferred, thứ tự và Group.
- [Spring Framework DeferredImportSelector.Group API](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/context/annotation/DeferredImportSelector.Group.html): `process`, `selectImports`, `Entry`.
- [Spring Framework ImportSelector API](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/context/annotation/ImportSelector.html): return type của selector, metadata và exclusion filter.
- [Spring Framework — Composing Java-based Configurations](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/composing-configuration-classes.html): cơ chế `@Import` khi ghép cấu hình.
- [Spring Framework — Container Extension Points](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-extension.html): phân biệt metadata post-processing và bean instance post-processing.
- [Spring Boot AutoConfigurationImportSelector API](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/autoconfigure/AutoConfigurationImportSelector.html): ứng dụng của deferred selection trong Boot.

**Kết luận:** `DeferredImportSelector` trì hoãn **import selection trong pha phân tích cấu hình**, không trì hoãn **bean initialization** tới runtime. `DeferredImportSelector.Group` cho phép phối hợp nhiều kết quả import theo policy chung. Hãy đo thời điểm bằng **parser/selector callbacks**, và luôn tách timing của metadata processing khỏi timing tạo bean instances.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="configuration-composition-007">Đăng ký định nghĩa bằng ImportBeanDefinitionRegistrar</a>

<details>
<summary>Xem chi tiết</summary>

# Đăng ký định nghĩa bằng ImportBeanDefinitionRegistrar trong Spring Core Container

## 1. Mục tiêu và phạm vi

Trong Spring Framework, đa số bean được khai báo bằng `@Component`/`@Service`, phương thức `@Bean` trong một class `@Configuration`, hoặc cấu hình XML. Nhưng thư viện hạ tầng, starter, module tích hợp và các annotation kiểu `@Enable...` đôi khi cần **tự tạo bean definitions một cách có điều kiện và có cấu hình** ngay trong lúc Spring xử lý cấu hình Java.

Interface **`ImportBeanDefinitionRegistrar`** là một điểm mở rộng dành cho việc đó. Một class triển khai registrar có thể được đưa vào cấu hình thông qua `@Import`. Spring gọi registrar trong quá trình phân tích configuration classes và trao cho nó:

- `AnnotationMetadata`: metadata annotation của class đang import registrar.
- `BeanDefinitionRegistry`: registry của container hiện tại, dùng để kiểm tra và thêm bean definitions.
- Trong overload ba tham số, một `BeanNameGenerator` được Spring dùng cho những imported beans.

Registrar chỉ định nghĩa **bean cần được Spring tạo như thế nào**; nó không nên tự chạy nghiệp vụ, tự dựng toàn bộ ApplicationContext, hoặc coi `registerBeanDefinition` là một lệnh khởi tạo instance ngay lập tức.

Sau bài học, bạn có thể:

1. Hiểu vị trí của `ImportBeanDefinitionRegistrar` trong quy trình `@Configuration` + `@Import`.
2. Biết lúc nào chọn registrar thay cho `@Bean`, `@ComponentScan`, `ImportSelector` hoặc `BeanDefinitionRegistryPostProcessor`.
3. Triển khai hai overload `registerBeanDefinitions(...)` đúng mục đích.
4. Đọc giá trị annotation tùy chỉnh qua `AnnotationMetadata`.
5. Dựng `BeanDefinition` với `BeanDefinitionBuilder`; đăng ký bằng `BeanDefinitionRegistry.registerBeanDefinition`.
6. Kiểm soát bean names, xung đột, constructor arguments, role, scope, qualifier và dependency injection.
7. Chạy ví dụ Java 17/Spring Framework có custom `@EnableGreetings` tạo hai bean, được inject vào bean `@Bean` thông thường.
8. Kiểm thử sự khác biệt giữa **bean definition được đăng ký** và **instance do BeanFactory tạo**.
9. Nhận biết giới hạn lifecycle/early initialization, đặc biệt việc không đăng ký `BeanDefinitionRegistryPostProcessor` từ registrar.
10. Thiết kế registrar gọn, deterministic và có hành vi lỗi rõ ràng.

**Thông điệp trung tâm:** Registrar là **hạ tầng đăng ký metadata vào IoC container**, không phải một Service Locator hay factory nghiệp vụ. Hãy để Spring quản lý việc khởi tạo, dependency resolution và lifecycle sau khi definition được thêm.

## 2. BeanDefinition là gì? Registrar thay đổi phần nào?

Một Spring bean không nhất thiết xuất phát từ một lời gọi `new` trong code của bạn. Container xử lý **bean definitions**, trong đó có thể chứa:

- Tên hoặc tham chiếu tới bean class.
- Constructor arguments hoặc property values.
- Scope (`singleton`, `prototype` hoặc scope đã đăng ký).
- Lazy initialization flag.
- Qualifiers và lựa chọn autowire candidates.
- Init/destroy callbacks và metadata về nguồn gốc/role.
- Factory method hoặc supplier nếu sử dụng phương thức tạo nâng cao.

Ví dụ Java config thông thường:

```java
@Configuration(proxyBeanMethods = false)
class AppConfig {
    @Bean
    EmailClient emailClient() {
        return new EmailClient();
    }
}
```

Spring đọc `@Bean` và xây definition cho `emailClient`. Registrar đi vào **tầng thấp hơn**: bạn tự quyết định definition nào cần xuất hiện, với tên gì và metadata nào, mà không cần viết một phương thức `@Bean` cố định cho mỗi trường hợp.

Luồng khái niệm:

```text
@Configuration AppConfig
       |
       | có @Import(MyRegistrar.class)
       v
ConfigurationClassPostProcessor phân tích Java config
       |
       v
MyRegistrar.registerBeanDefinitions(...)
       |
       | AnnotationMetadata + BeanDefinitionRegistry
       v
Tạo BeanDefinition metadata
       |
       | registry.registerBeanDefinition(beanName, definition)
       v
BeanFactory có thêm definition
       |
       | resolve dependencies / scopes / lifecycle
       v
Bean instance được container tạo theo nhu cầu
```

**Điểm rất dễ nhầm:** `registerBeanDefinition("x", def)` **không đồng nghĩa** với `new X()` ngay tại thời điểm gọi. Việc tạo instance thường diễn ra sau đó, khi container pre-instantiate singleton không lazy hoặc khi ứng dụng thực sự yêu cầu bean theo scope/lifecycle.

Registrar được gọi **trong giai đoạn xử lý configuration**, nên cần tập trung vào quyết định metadata. Các thao tác như chạy migrations, gọi remote API, mở connection pool hay xử lý hóa đơn không thuộc trách nhiệm ở đây.

## 3. Interface và những API liên quan

Interface nằm trong:

```java
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
```

Hai phương thức được quan tâm:

```java
// Chữ ký minh họa, bỏ qua generic imports đầy đủ:
default void registerBeanDefinitions(
        AnnotationMetadata importingClassMetadata,
        BeanDefinitionRegistry registry) {
}

default void registerBeanDefinitions(
        AnnotationMetadata importingClassMetadata,
        BeanDefinitionRegistry registry,
        BeanNameGenerator importBeanNameGenerator) {
    registerBeanDefinitions(importingClassMetadata, registry);
}
```

Trong Spring 6.2, hai method là **default methods**; overload ba tham số mặc định **ủy quyền** cho overload hai tham số. Vì vậy, chỉ cần override phương thức hai tham số cho trường hợp thông thường.

### 3.1. AnnotationMetadata

`org.springframework.core.type.AnnotationMetadata` biểu diễn metadata của **class được import**, không phải instance của class đó. Bạn có thể đọc annotation hiện diện trực tiếp hoặc meta-present qua các API thích hợp.

Ví dụ:

```java
Map<String, Object> values =
        importingClassMetadata.getAnnotationAttributes(
                EnableGreetings.class.getName());
```

Ở đây `EnableGreetings` là custom annotation; ví dụ đầy đủ ở mục 7. Method trả `null` nếu không tìm thấy annotation theo chính sách truy vấn đó. Không cố cast `null` hoặc tin rằng mọi nơi import registrar đều mang annotation mong muốn.

**Ưu điểm:** Có thể ra quyết định đăng ký bean từ metadata mà không cần khởi tạo đối tượng configuration class, không chạy constructor hay business code của class ấy.

### 3.2. BeanDefinitionRegistry

`org.springframework.beans.factory.support.BeanDefinitionRegistry` là API để:

- `registerBeanDefinition(String, BeanDefinition)`: đăng ký definition với tên cụ thể.
- `containsBeanDefinition(String)`: xem registry đã có definition mang tên đó.
- `getBeanDefinition(String)`: xem definition đã đăng ký.
- `getBeanDefinitionNames()`: lấy các tên definitions hiện có.
- Một số thao tác registry khác trong những tình huống hạ tầng phù hợp.

API còn tham gia cơ chế alias; nếu dùng tên tạo động, kiểm tra cả khả năng tên trùng alias hoặc một definition khác trong cùng registry khi thiết kế collision policy.

Đừng coi registry giống `ApplicationContext` để gọi `getBean` và chạy use case. Nó chủ yếu thao tác metadata trong giai đoạn bootstrap.

### 3.3. BeanDefinitionBuilder

`org.springframework.beans.factory.support.BeanDefinitionBuilder` giúp tạo definition tương đối an toàn và dễ đọc:

```java
BeanDefinition definition =
        BeanDefinitionBuilder
                .genericBeanDefinition(GreetingHandler.class)
                .addConstructorArgValue("email")
                .getBeanDefinition();
registry.registerBeanDefinition("greeting.email", definition);
```

Đây là **đoạn minh họa** cần có `GreetingHandler`, `registry` và các imports tương ứng. Chúng ta không tự gọi `new GreetingHandler("email")`; Spring sẽ dùng definition để gọi constructor khi tạo bean.

Có thể chọn `RootBeanDefinition` trực tiếp cho trường hợp cần kiểm soát metadata sâu hơn:

```java
RootBeanDefinition definition =
        new RootBeanDefinition(GreetingHandler.class);
definition.getConstructorArgumentValues()
        .addGenericArgumentValue("email");
registry.registerBeanDefinition("greeting.email", definition);
```

Hai ví dụ thể hiện hai cách tạo definition cho cùng ý tưởng; không đặt cả hai cạnh nhau trong cùng registrar để đăng ký trùng tên.

## 4. Registrar được kích hoạt bằng @Import

Một registrar cần được **Spring configuration processing biết đến**. Cách phổ biến nhất:

```java
@Configuration(proxyBeanMethods = false)
@Import(GreetingRegistrar.class)
public class AppConfig {
}
```

Spring phân tích `AppConfig`, thấy `@Import` trỏ tới class implement `ImportBeanDefinitionRegistrar` và gọi method đăng ký definitions. Đây không phải component scan và cũng không phải một lời gọi do developer phải thực thi thủ công.

### 4.1. Dùng trực tiếp @Import

```java
@Import(GreetingRegistrar.class)
class AppConfig {
}
```

Phù hợp để thử nghiệm hoặc khi việc import registrar là chi tiết hạ tầng mà cấu hình ứng dụng được phép nhìn thấy.

### 4.2. Tạo custom @Enable annotation

Một thư viện thường muốn API đẹp hơn:

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(GreetingRegistrar.class)
public @interface EnableGreetings {
    String[] value();
}
```

Sau đó người dùng viết:

```java
@Configuration(proxyBeanMethods = false)
@EnableGreetings({"email", "sms"})
class AppConfig {
}
```

Spring nhận diện `@Import` nằm trên **meta-annotation `@EnableGreetings`**, gọi registrar và cho registrar đọc danh sách channels từ metadata của `AppConfig`. Không cần người dùng tự gọi BeanFactory hoặc viết N phương thức `@Bean`.

Cách ghép `@Enable...` này rất phù hợp cho một feature có cấu hình nhỏ nhưng cần đăng ký số lượng beans thay đổi theo annotation.

### 4.3. @Import không phải là khởi tạo registrar như một business bean

Framework tạo và sử dụng registrar trong luồng xử lý configuration. Bạn không nên giả định registrar là singleton service nghiệp vụ có cùng lifecycle với bean ứng dụng bình thường, hoặc đưa side effects vào constructor của nó.

Registrar cũng có thể được **ImportSelector trả về**; đây là trường hợp cấu hình nâng cao, nhưng điểm cốt lõi không đổi: Spring gọi registrar khi xử lý class configuration phù hợp.

## 5. Thứ tự xử lý: vì sao registrar không nên tự lấy bean ngay?

Một lộ trình điển hình khi `AnnotationConfigApplicationContext` refresh:

```text
register(AppConfig.class)
        |
        v
refresh()
        |
        v
ConfigurationClassPostProcessor
        |
        v
Phân tích @Configuration, @Import, @Bean, component metadata
        |
        v
Gọi ImportBeanDefinitionRegistrar
        |
        v
Registry có thêm bean definitions
        |
        v
BeanFactoryPostProcessor / BeanPostProcessor theo lifecycle
        |
        v
Pre-instantiate non-lazy singletons
        |
        v
Dependency injection, initialization và post-processing
        |
        v
Context sẵn sàng cho application code
```

Sơ đồ chỉ diễn tả **thứ tự khái niệm**, không phải mô tả toàn bộ thuật toán nội bộ. Những post-processors nhất định tham gia rất sớm; không nên dựa vào chi tiết thứ tự của mọi definition nếu không có hợp đồng API.

Ở thời điểm registrar được gọi:

- Nhiều bean definitions có thể đã tồn tại; nhiều beans vẫn **chưa được tạo**.
- Những quyết định như tên bean, constructor values, scope, candidate metadata nên được ghi vào definition.
- Việc resolve `@Autowired` hay gọi business method có thể chưa an toàn.
- Không cố lấy một bean để hỏi giá trị cấu hình nếu có thể đọc từ `AnnotationMetadata` hoặc `Environment`.
- Không tạo `AnnotationConfigApplicationContext` phụ từ registrar chỉ để instantiate đối tượng.

Đặc biệt, **Javadoc Spring cảnh báo** rằng không đăng ký các bean types thuộc `BeanDefinitionRegistryPostProcessor` thông qua `ImportBeanDefinitionRegistrar` do ràng buộc lifecycle của configuration class processing. Nếu cần một registry post-processor đúng nghĩa, hãy sử dụng điểm mở rộng/đường đăng ký tương ứng, không lách bằng một registrar đăng ký post-processor của chính nó.

## 6. Lúc nào nên chọn registrar?

| Nhu cầu | Công cụ ưu tiên cân nhắc | Lý do |
| --- | --- | --- |
| Một bean cố định, được dựng qua Java | `@Bean` | Rõ kiểu và dependency |
| Class ứng dụng có stereotype | `@Component`/`@Service` + component scan | Ít boilerplate |
| Chia Java configuration thành module | `@Import(Config.class)` | Composition trực tiếp |
| Chọn một/tập configuration classes có điều kiện | `ImportSelector` | Quyết định những classes nào được import |
| Tạo nhiều bean definitions phụ thuộc annotation metadata | `ImportBeanDefinitionRegistrar` | Đăng ký definitions linh hoạt |
| Thay đổi/duyệt definitions ở giai đoạn BeanFactory post-process | `BeanDefinitionRegistryPostProcessor`/`BeanFactoryPostProcessor` | Điểm mở rộng riêng, cần hiểu ordering/lifecycle |
| Chọn tên, scope, constructor arguments một cách programmatic trong context | `BeanDefinitionRegistry` hoặc `GenericApplicationContext.registerBean` | Đăng ký bằng API, không nhất thiết cần @Import |
| Nhập XML vào Java config | `@ImportResource` | Đọc bean definitions từ XML |
| Runtime chọn một strategy cụ thể | Registry/factory nghiệp vụ hoặc qualifier | Không phải lý do tự động dùng registrar |

**Registrar đáng dùng khi số lượng hoặc metadata bean phụ thuộc vào annotation/configuration** mà một số ít phương thức `@Bean` không mô tả gọn. Nó là extension point mức framework/infrastructure; tránh thêm nó chỉ vì muốn code “linh hoạt hơn” khi ứng dụng chỉ cần một bean đơn giản.

### Khi không nên dùng

Không nên dùng registrar chỉ để đăng ký:

```java
@Bean
PaymentGateway paymentGateway() {
    return new DefaultPaymentGateway();
}
```

nếu không có nhu cầu điều kiện/tên/metadata động. `@Bean` rõ ràng hơn, compile-time type references dễ thấy, ít phụ thuộc framework internals và ít rủi ro registration conflict.

Không dùng registrar làm nơi gửi email, migrate dữ liệu hoặc tạo network connection khi Spring đang xử lý configuration definitions.

## 7. Dự án runnable Java 17: custom @EnableGreetings đăng ký hai bean

Ví dụ chạy với **JDK 17 + Spring Framework 6.2.x**, không cần Spring Boot. Mục tiêu là nhìn thấy từ đầu đến cuối:

1. `@EnableGreetings({"email", "sms"})` chứa cấu hình feature.
2. `@Import(GreetingRegistrar.class)` meta-present giúp Spring gọi registrar.
3. Registrar đọc `AnnotationMetadata` và đăng ký hai bean definitions với tên `greeting.email`, `greeting.sms`.
4. `@Bean GreetingDashboard` được Spring tiêm `GreetingHandler` với `@Qualifier("greeting.email")`; điều đó chứng minh definition do registrar tạo tham gia dependency resolution bình thường.
5. `getBeanNamesForType` và `getBeanDefinition` xác minh đúng definitions, names và roles.
6. Không component scan và không `@Bean` cho từng `GreetingHandler`; việc tạo chúng hoàn toàn do registrar đăng ký metadata.

### 7.1. Cấu trúc thư mục

```text
registrar-demo/
├── pom.xml
└── src/main/java/
    └── demo/
        └── RegistrarDemo.java
```

### 7.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="
             http://maven.apache.org/POM/4.0.0
             https://maven.apache.org/xsd/maven-4.0.0.xsd">

    <modelVersion>4.0.0</modelVersion>

    <groupId>demo</groupId>
    <artifactId>registrar-demo</artifactId>
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

### 7.3. Toàn bộ file RegistrarDemo.java

Tạo `src/main/java/demo/RegistrarDemo.java`:

```java
package demo;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

public class RegistrarDemo {

    // Service object được quản lý bởi Spring sau khi registrar
    // đăng ký BeanDefinition tương ứng.
    public static final class GreetingHandler {
        private final String channel;

        public GreetingHandler(String channel) {
            this.channel = channel;
        }

        public String greet(String name) {
            return channel + ":Hello, " + name;
        }
    }

    public static final class GreetingDashboard {
        private final GreetingHandler email;

        public GreetingDashboard(GreetingHandler email) {
            this.email = email;
        }

        public String headline(String name) {
            return "dashboard=" + email.greet(name);
        }
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @Import(GreetingRegistrar.class)
    public @interface EnableGreetings {
        String[] value();
    }

    public static final class GreetingRegistrar
            implements ImportBeanDefinitionRegistrar {

        @Override
        public void registerBeanDefinitions(
                AnnotationMetadata importingClassMetadata,
                BeanDefinitionRegistry registry) {

            Map<String, Object> attributes =
                    importingClassMetadata.getAnnotationAttributes(
                            EnableGreetings.class.getName());

            if (attributes == null) {
                throw new IllegalStateException(
                        "@EnableGreetings is required");
            }

            String[] channels = (String[]) attributes.get("value");
            Set<String> seen = new HashSet<>();

            for (String channel : channels) {
                if (channel == null ||
                        !channel.matches("[a-z][a-z0-9-]*")) {
                    throw new IllegalArgumentException(
                            "Invalid channel: " + channel);
                }

                if (!seen.add(channel)) {
                    throw new IllegalArgumentException(
                            "Duplicate channel: " + channel);
                }

                String beanName = "greeting." + channel;
                if (registry.containsBeanDefinition(beanName) ||
                        registry.isAlias(beanName)) {
                    throw new IllegalStateException(
                            "Bean name already used: " + beanName);
                }

                BeanDefinition definition = BeanDefinitionBuilder
                        .genericBeanDefinition(GreetingHandler.class)
                        .addConstructorArgValue(channel)
                        .getBeanDefinition();

                definition.setRole(BeanDefinition.ROLE_APPLICATION);
                registry.registerBeanDefinition(beanName, definition);
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableGreetings({"email", "sms"})
    public static class AppConfig {

        @Bean
        public GreetingDashboard greetingDashboard(
                @Qualifier("greeting.email") GreetingHandler email) {
            return new GreetingDashboard(email);
        }
    }

    public static void main(String[] args) {
        try (var context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {

            String[] beanNames =
                    context.getBeanNamesForType(GreetingHandler.class);
            Arrays.sort(beanNames);

            System.out.println("handlers=" + Arrays.toString(beanNames));
            System.out.println("email=" +
                    context.getBean("greeting.email",
                                    GreetingHandler.class).greet("Lan"));
            System.out.println("sms=" +
                    context.getBean("greeting.sms",
                                    GreetingHandler.class).greet("Lan"));
            System.out.println(
                    context.getBean(GreetingDashboard.class).headline("Mai"));
            System.out.println("roleIsApplication=" +
                    (context.getBeanDefinition("greeting.email").getRole()
                            == BeanDefinition.ROLE_APPLICATION));
        }
    }
}
```

### 7.4. Chạy chương trình

Từ thư mục chứa `pom.xml`, với JDK 17+ và Maven:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.RegistrarDemo
```

Output **do chương trình in ra** (không tính logging của Maven/Spring nếu môi trường có cấu hình khác):

```text
handlers=[greeting.email, greeting.sms]
email=email:Hello, Lan
sms=sms:Hello, Lan
dashboard=email:Hello, Mai
roleIsApplication=true
```

### 7.5. Vì sao output như trên?

**`handlers=[greeting.email, greeting.sms]`:** Registrar đăng ký hai bean definitions có class `GreetingHandler`. Spring đã tạo/đã có thể phân giải chúng theo type. `getBeanNamesForType(GreetingHandler.class)` trả về tên chính của các bean ứng viên theo type, còn `Arrays.sort` bảo đảm thứ tự hiển thị độc lập thứ tự nội bộ của registry.

**`email=email:Hello, Lan`** và **`sms=sms:Hello, Lan`:** Mỗi `BeanDefinition` có constructor argument là chuỗi channel tương ứng. BeanFactory gọi `GreetingHandler(String)` để tạo instance khi cần, không phải registrar tạo sẵn object. Cả hai beans có class giống nhau nhưng constructor state khác nhau.

**`dashboard=email:Hello, Mai`:** `GreetingDashboard` được tạo bởi Java `@Bean`. Tham số `@Qualifier("greeting.email") GreetingHandler` được Spring resolve tới bean registrar đã đăng ký. Vì vậy một bean Java config bình thường có thể phụ thuộc vào bean được đăng ký programmatically.

**`roleIsApplication=true`:** Registrar đặt `BeanDefinition.ROLE_APPLICATION` cho definition `greeting.email`; truy vấn lại metadata xác nhận giá trị này. Role chủ yếu là hint phân loại cho các công cụ/container, **không** phải quy tắc bắt buộc về singleton, injection hoặc mức độ ưu tiên của bean.

### 7.6. Tại sao dùng public static nested classes?

Mọi class trong ví dụ nằm trong một Java file để tiện sao chép. `GreetingHandler`, `GreetingDashboard` và `GreetingRegistrar` đều là **public static nested classes**, nên không cần một instance `RegistrarDemo` bên ngoài khi được Spring tạo.

`GreetingHandler` có public constructor nhận `String channel`; `BeanDefinitionBuilder.addConstructorArgValue(channel)` cung cấp đúng giá trị cho constructor đó. Nếu đổi thành non-static inner class, Spring còn cần một outer instance và mẫu bean definition sẽ không còn đơn giản như ví dụ.

### 7.7. Vì sao không cần Spring Boot?

`AnnotationConfigApplicationContext`, `@Import`, `ImportBeanDefinitionRegistrar`, `BeanDefinitionRegistry` và `BeanDefinitionBuilder` đều là API của Spring Framework. Dependency `spring-context` kéo theo các module core/beans cần thiết, nên ví dụ này chạy như một console application thông thường.

Nếu sau này đặt một custom `@EnableGreetings` trong Spring Boot application, cơ chế registrar vẫn thuộc nền tảng Spring Framework; Boot không phải điều kiện bắt buộc để registrars hoạt động.

## 8. Đọc AnnotationMetadata đúng và an toàn

Registrar thường được nhập qua một composed annotation có attributes như:

```java
@EnableGreetings({"email", "sms"})
class AppConfig {
}
```

Trong registrar:

```java
Map<String, Object> attributes =
        importingClassMetadata.getAnnotationAttributes(
                EnableGreetings.class.getName());

if (attributes == null) {
    throw new IllegalStateException("Missing @EnableGreetings");
}
String[] channels = (String[]) attributes.get("value");
```

Đây là mã trích từ ví dụ đầy đủ. `importingClassMetadata` đại diện cho class cấu hình đã khai báo annotation. Với `@EnableGreetings` trong ví dụ, `value` khai báo là `String[]`, nên có thể cast sang `String[]`.

### Sai lầm: đọc metadata của registrar thay vì class import

`GreetingRegistrar.class.getAnnotation(EnableGreetings.class)` sẽ không cho bạn danh sách channels trong `AppConfig`, vì annotation `@EnableGreetings` nằm trên **AppConfig**, không nằm trên registrar. Spring trao cho registrar metadata của **importing configuration class** chính để giải quyết vấn đề này.

### Khi annotation được meta-annotated thêm nhiều tầng

Nếu custom annotation kết hợp thêm các meta-annotations hoặc alias, hãy dùng các API annotation metadata/merged annotations của Spring đúng mục đích thay vì chỉ duyệt Java reflection trực tiếp. Mục tiêu là đọc metadata cấu hình **không cần instantiate class cấu hình**.

### Validation trước khi đăng ký

Các giá trị từ annotation có thể dẫn đến bean names, class names, constructor args hoặc số lượng definitions. Nên kiểm tra:

- Giá trị rỗng/null/không hợp lệ.
- Giá trị lặp hoặc trùng tên sau khi chuẩn hóa.
- Quy tắc bean naming và những ký tự bị cấm bởi **hợp đồng ứng dụng**.
- Số lượng bean sẽ tạo, tránh vô tình hàng nghìn definitions từ một danh sách quá lớn.
- Các điều kiện feature flags có deterministic và đủ thông tin ở giai đoạn bootstrap không.

Ví dụ `channel.matches("[a-z][a-z0-9-]*")` là **quy ước riêng của demo**, không phải giới hạn tên bean toàn cục do Spring áp đặt.

## 9. BeanDefinitionBuilder và metadata của definition

Một builder có thể thiết lập nhiều thuộc tính:

```java
BeanDefinitionBuilder builder =
        BeanDefinitionBuilder.genericBeanDefinition(Worker.class)
                .addConstructorArgValue("batch")
                .setScope("prototype")
                .setLazyInit(true);

BeanDefinition definition = builder.getBeanDefinition();
definition.setRole(BeanDefinition.ROLE_SUPPORT);
registry.registerBeanDefinition("worker.batch", definition);
```

Đây là **đoạn minh họa** cần class `Worker` có constructor thích hợp. Có thể dùng `BeanDefinition.SCOPE_PROTOTYPE` hay `ConfigurableBeanFactory.SCOPE_PROTOTYPE` khi muốn tránh chuỗi scope. Cần cân nhắc:

- `setScope("prototype")`: bean factory tạo instance mới theo mỗi lượt phân giải prototype.
- `setLazyInit(true)`: có ý nghĩa đặc biệt với singleton; prototype vốn thường được tạo khi được yêu cầu.
- `ROLE_SUPPORT`: hint vai trò, không phải component scanning annotation.
- `addConstructorArgValue(...)`: lưu giá trị constructor để Spring giải quyết khi tạo instance.

### 9.1. Đừng nhầm BeanDefinition với bean instance

Có definition:

```java
registry.registerBeanDefinition("worker.batch", definition);
```

không có nghĩa `new Worker("batch")` đã chạy. Trong singleton non-lazy bình thường, container có thể instantiate ở giai đoạn refresh; trong prototype, instance xuất hiện khi được yêu cầu.

Nếu bạn truyền object thật vào builder qua `addConstructorArgValue`, object đó là argument value; không phải container tự biến object ấy thành một bean có lifecycle độc lập.

### 9.2. Property values

Nếu class đích có setter phù hợp:

```java
BeanDefinition definition =
        BeanDefinitionBuilder
                .genericBeanDefinition(ExampleClient.class)
                .addPropertyValue("endpoint", "http://localhost")
                .getBeanDefinition();
registry.registerBeanDefinition("exampleClient", definition);
```

Đây là minh họa; `ExampleClient` cần method `setEndpoint(String)`. `addPropertyValue` tạo metadata cho property injection; bean vẫn được Spring tạo về sau.

### 9.3. Dependency tới bean khác

Để tham chiếu một bean có tên đã biết, có thể dùng `RuntimeBeanReference`:

```java
import org.springframework.beans.factory.config.RuntimeBeanReference;

BeanDefinition definition = BeanDefinitionBuilder
        .genericBeanDefinition(ReportService.class)
        .addConstructorArgValue(
                new RuntimeBeanReference("reportRepository"))
        .getBeanDefinition();

registry.registerBeanDefinition("reportService", definition);
```

Đây là minh họa cần `ReportService(ReportRepository)` và bean `reportRepository` tồn tại. `RuntimeBeanReference` không phải chuỗi tên được truyền làm giá trị constructor; nó là metadata để Spring phân giải **bean reference** vào lúc tạo bean.

Nếu phụ thuộc có thể được biểu diễn bằng constructor autowiring thông thường, bạn cũng có thể để Spring tự giải quyết theo kiểu, nhưng hãy kiểm tra API, mode và metadata phù hợp. Đừng tự gọi `BeanFactory.getBean` từ registrar để đưa object vào definition sớm.

### 9.4. Role và autowire-candidate

`BeanDefinition.ROLE_APPLICATION`, `ROLE_SUPPORT` và `ROLE_INFRASTRUCTURE` là các hints cho vai trò bean. Chúng không tự cho biết bean có được autowire hay không; autowire eligibility chịu các metadata và quy tắc dependency khác.

Nếu registrar tạo helper bean không được phép trở thành ứng viên injection thông thường, có thể cân nhắc `definition.setAutowireCandidate(false)`. Khi đó phải bảo đảm không làm hỏng dependency thật sự cần bean helper. Không đánh dấu `ROLE_INFRASTRUCTURE` rồi nghĩ Spring tự tắt autowiring.

## 10. Hai overload của registerBeanDefinitions

### 10.1. Overload hai tham số

```java
@Override
public void registerBeanDefinitions(
        AnnotationMetadata importingClassMetadata,
        BeanDefinitionRegistry registry) {
    // Đọc metadata rồi đăng ký bean definitions.
}
```

Đây là lựa chọn đơn giản nhất và là cách ví dụ chạy đầy đủ sử dụng. Nó phù hợp khi bạn **tự đặt tên rõ ràng** như `greeting.email`, không cần được truyền BeanNameGenerator của framework.

### 10.2. Overload ba tham số

```java
@Override
public void registerBeanDefinitions(
        AnnotationMetadata importingClassMetadata,
        BeanDefinitionRegistry registry,
        BeanNameGenerator importBeanNameGenerator) {
    // Có thể dùng chiến lược đặt tên dành cho imported beans.
}
```

Tham số `importBeanNameGenerator` được cung cấp để registrar có thể tôn trọng naming strategy khi tạo các definitions theo cấu trúc framework. Nếu override overload ba tham số, Spring sẽ gọi trực tiếp implementation này; không phải tự gọi overload hai tham số trừ khi bạn muốn.

**Lưu ý:** Tên dành cho imported beans có default và có thể được tùy chỉnh bởi cấu hình `ConfigurationClassPostProcessor`. Không tự khẳng định rằng mọi registrar phải dùng `AnnotationBeanNameGenerator` cho mọi bean hoặc BeanNameGenerator sẽ tự chọn tên nếu bạn đã chủ động xây tên nghiệp vụ như `greeting.email`.

### Tránh override rỗng hoặc nhầm overload

Vì cả hai phương thức là default trong Spring Framework 6.2, bạn cần **override ít nhất một phương thức có ý nghĩa**. Nếu triển khai interface nhưng không override bất kỳ method nào, registrar sẽ không tạo definition nào — điều đó có thể biên dịch mà làm người dùng tưởng tính năng đã bật.

Đừng viết code trong overload hai tham số rồi override thêm overload ba tham số rỗng: phương thức ba tham số của bạn có thể chặn default delegation và khiến logic đăng ký không được chạy.

## 11. Đảm bảo bean names không va chạm

Registrar tạo tên programmatically nên phải xác định **collision policy** trước khi đăng ký.

Mẫu bảo thủ:

```java
String name = "greeting." + channel;

if (registry.containsBeanDefinition(name) || registry.isAlias(name)) {
    throw new IllegalStateException(
            "Bean name already used: " + name);
}

registry.registerBeanDefinition(name, definition);
```

**Phạm vi kiểm tra:** `containsBeanDefinition` xem definition đã hiện diện trong registry **tại thời điểm đó**. Một bean name khác có thể được đăng ký ở giai đoạn sau; vì vậy collision policy của registrar cần phối hợp với chính sách overriding của container và integration tests. Kiểm tra trước không phải bảo đảm chống mọi xung đột tương lai.

### Tự override bean definition có sẵn?

Một số BeanFactories có thể cho phép overriding. Nhưng registry-level customization không nên dựa vào “đăng ký cuối cùng sẽ thắng”, vì thứ tự và chính sách override có thể khác giữa môi trường/Boot config và phiên bản.

Nếu muốn extension point có khả năng người dùng override:

- Định nghĩa bean name cố định hoặc contract rõ ràng.
- Tài liệu hóa lúc nào registrar bỏ qua một definition đã tồn tại, lúc nào phải báo lỗi.
- Đảm bảo hành vi deterministic dưới các cách import config khác nhau.
- Integration test với trường hợp người dùng có custom bean cùng tên.
- Không âm thầm thay bean business-critical bằng implementation khác mà không có cảnh báo.

**Ví dụ đang dùng chính sách fail fast** khi có definition/alias trùng tên. Đây là lựa chọn có chủ đích của thư viện demo, không phải quy tắc bắt buộc cho mọi registrar.

## 12. Nhiều configuration classes cùng import registrar

Giả sử nhiều class cùng mang `@EnableGreetings` hoặc cùng import registrar qua nhiều tuyến:

```java
@Configuration
@EnableGreetings({"email"})
class EmailConfig {
}

@Configuration
@EnableGreetings({"sms"})
class SmsConfig {
}
```

Tình huống này cần **test cụ thể** về cách Spring xử lý imports, deduplication và metadata trong application context sử dụng thực tế. Không thiết kế thư viện dựa trên giả định “registrar chắc chắn được gọi đúng một lần cho toàn bộ JVM”.

Nếu registrar có thể được gọi nhiều lần:

- Đừng dùng static mutable counter để quyết định bean names.
- Xác định cùng bean name với cùng config được coi là idempotent hay lỗi.
- Khi mỗi importing class có thuộc tính khác nhau, logic nên xử lý từng tập metadata theo cơ chế framework hoặc chọn một cấu hình tổng hợp rõ ràng.
- Nếu hai nguồn đăng ký cùng tên, phải có collision policy nhất quán.
- Không dựa vào thứ tự import làm business priority.

Trong ví dụ đầy đủ chỉ có **một `AppConfig`** mang `@EnableGreetings({"email","sms"})`. Điều đó giúp output dễ kiểm chứng và tránh giả định chưa được kiểm thử về nhiều import chains.

## 13. Aware interfaces, constructor injection và Environment

Registrar có thể cần cấu hình môi trường hoặc resource loader **ở thời điểm xử lý definition**. Javadoc Spring cho phép registrar implement một số `Aware` interfaces:

- `EnvironmentAware`.
- `BeanFactoryAware`.
- `BeanClassLoaderAware`.
- `ResourceLoaderAware`.

Spring gọi các callback phù hợp **trước khi** chạy `registerBeanDefinitions(...)`. Ngoài ra, registrar có thể có **một constructor** với một hoặc nhiều tham số thuộc các kiểu được hỗ trợ:

- `Environment`.
- `BeanFactory`.
- `ClassLoader`.
- `ResourceLoader`.

### Ví dụ đọc Environment

```java
// Đoạn minh họa bên trong registrar, không phải project độc lập.
final class FeatureRegistrar
        implements ImportBeanDefinitionRegistrar, EnvironmentAware {

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void registerBeanDefinitions(
            AnnotationMetadata importingClassMetadata,
            BeanDefinitionRegistry registry) {
        boolean enabled = environment.getProperty(
                "features.reporting.enabled", Boolean.class, false);
        if (!enabled) {
            return;
        }

        BeanDefinition definition = BeanDefinitionBuilder
                .genericBeanDefinition(ReportAdapter.class)
                .getBeanDefinition();
        registry.registerBeanDefinition("reportAdapter", definition);
    }
}
```

Đây là ví dụ yêu cầu `ReportAdapter` và imports liên quan. Đọc `Environment` tại giai đoạn configuration có thể hợp lý nếu kết quả **ổn định cho lần khởi tạo context** và không đòi tạo các bean nghiệp vụ trước.

### Không dùng BeanFactoryAware để chạy business logic

Việc registrar được trao `BeanFactory` không có nghĩa mọi bean đã khởi tạo. Tránh gọi `getBean` từ registrar để mở kết nối, chạy migration hoặc kích hoạt workflow. Nếu thực sự cần trao dependency cho bean tạo về sau, sử dụng constructor arguments, references, qualifiers hoặc BeanFactory resolution sau này.

### ClassLoader và ResourceLoader

Có thể dùng khi bạn cần đọc metadata/resource phục vụ quyết định đăng ký. Cần thận trọng với class loading động và AOT/native image. Nếu danh sách class đến từ nguồn không tin cậy, validate allowlist và đừng cho phép registration class tùy ý chỉ từ cấu hình người dùng.

## 14. So sánh ImportBeanDefinitionRegistrar với BeanDefinitionRegistryPostProcessor

Tên hai extension points rất giống nhau, nhưng chúng xuất hiện ở **ngữ cảnh và thời điểm khác**.

| Tiêu chí | `ImportBeanDefinitionRegistrar` | `BeanDefinitionRegistryPostProcessor` |
| --- | --- | --- |
| Cách tham gia điển hình | `@Import(Registrar.class)` hoặc `ImportSelector` | Bean post-processor mức BeanFactory/registry |
| Input quan trọng | `AnnotationMetadata` của importing class + registry | `BeanDefinitionRegistry` trong callback post-processing |
| Trọng tâm | Thêm definitions dựa trên imported configuration metadata | Kiểm tra/điều chỉnh/đăng ký definitions ở post-process phase |
| Nhu cầu @Enable annotation | Rất phù hợp | Không phải giao diện chính cho composed @Enable |
| Có thể thêm bean definitions | Có | Có, theo lifecycle tương ứng |
| Được đăng ký bằng registrar làm post-processor mới | **Không nên/không được theo Javadoc** | Phải đưa vào đúng đường lifecycle, không vòng qua registrar |

Quan trọng nhất: Javadoc của `ImportBeanDefinitionRegistrar` ghi rõ **`BeanDefinitionRegistryPostProcessor` types may not be registered here**, do lifecycle constraints khi Spring xử lý `@Configuration` classes. Đừng phớt lờ cảnh báo này hoặc dùng đăng ký trực tiếp qua registrar để thay một post-processor hạ tầng đặc biệt.

Nếu bạn cần mutate các definitions đã được nhiều nguồn đăng ký hoặc chạy theo ordering của post-processors, hãy tìm hiểu riêng `BeanDefinitionRegistryPostProcessor`. Nếu mục tiêu là thực thi API `@EnableFeature` dựa trên annotation attributes để thêm beans, registrar thường là lựa chọn trực diện hơn.

## 15. Quan hệ với @ImportSelector và DeferredImportSelector

`@Import` có thể xử lý:

- Class `@Configuration` bình thường.
- Class component phù hợp.
- Class implement `ImportSelector`.
- Class implement `ImportBeanDefinitionRegistrar`.

`ImportSelector` trả về **tên class** được import, ví dụ các configuration classes tùy điều kiện. Nó không trực tiếp gọi `registry.registerBeanDefinition(...)` trong interface chính. Registrar thì **tự tạo bean definitions** dựa trên metadata.

Ví dụ ý tưởng:

```java
// Minh họa API, không phải ví dụ runnable.
public class MySelector implements ImportSelector {
    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        return new String[]{SpecificConfig.class.getName()};
    }
}
```

`DeferredImportSelector` còn có thời điểm xử lý/trình tự khác, thường hữu ích cho tập cấu hình chọn sau một số bước phân tích configuration; trong Spring Boot, nhiều cơ chế auto-configuration có tổ chức phức tạp hơn sử dụng các extension points tương ứng.

Đừng hoán đổi selector và registrar một cách cơ học. Chọn theo **loại đầu ra cần tạo**:

- Chọn configuration classes: selector.
- Đăng ký definitions tùy metadata: registrar.
- Khai báo 1–2 beans cố định: `@Bean` thường đủ.

Một `ImportSelector` cũng có thể trả tên registrar class để Spring tiếp tục xử lý theo pipeline import. Đó là trường hợp thư viện thực sự cần chọn registrar theo điều kiện.

## 16. Thực hành với scopes và lazy beans

Registrar có thể tạo nhiều definitions với scopes khác nhau. Nhưng đó chỉ là metadata, không phải hành vi của Java constructor.

### Singleton (mặc định)

```java
BeanDefinition def =
        BeanDefinitionBuilder.genericBeanDefinition(Worker.class)
                .getBeanDefinition();
registry.registerBeanDefinition("worker", def);
```

Nếu không đổi scope, default là singleton. Trong một ApplicationContext, `context.getBean("worker")` nhiều lần thường trả cùng object.

### Prototype

```java
BeanDefinition def = BeanDefinitionBuilder
        .genericBeanDefinition(Worker.class)
        .setScope("prototype")
        .getBeanDefinition();
registry.registerBeanDefinition("worker", def);
```

BeanFactory có thể tạo instance mới mỗi lần nó phân giải prototype. Nếu singleton inject prototype một lần vào constructor, singleton thường giữ instance đó; muốn lấy prototype mỗi job, dùng `ObjectProvider<Worker>` hoặc factory chuyên dụng.

### Lazy singleton

```java
BeanDefinition def = BeanDefinitionBuilder
        .genericBeanDefinition(Worker.class)
        .setLazyInit(true)
        .getBeanDefinition();
registry.registerBeanDefinition("worker", def);
```

Lazy singleton có thể chưa được tạo trong pre-instantiation phase; nhưng một dependency eager khác có thể yêu cầu nó sớm. Đừng nhầm `setLazyInit(true)` với “definition sẽ chưa tồn tại trong registry”: definition có sẵn; **instance** mới có thể bị trì hoãn.

**Lưu ý:** `BeanDefinitionBuilder` thay đổi metadata. Đừng gọi `new Worker()` trong registrar rồi tự giữ object trong static map; điều đó bỏ qua IoC semantics mong muốn.

## 17. Tên bean, qualifiers và số lượng ứng viên

Giả sử registrar đăng ký hai bean cùng class:

```text
greeting.email -> GreetingHandler("email")
greeting.sms   -> GreetingHandler("sms")
```

Một injection point:

```java
GreetingDashboard(GreetingHandler handler) {
    this.handler = handler;
}
```

có thể nhập nhằng nếu Spring không có ứng viên thắng theo quy tắc lựa chọn. Nhưng:

```java
GreetingDashboard(
        @Qualifier("greeting.email") GreetingHandler handler) {
    this.handler = handler;
}
```

yêu cầu bean cụ thể. Trong dự án ví dụ, qualifier được đặt ngay trên tham số `@Bean` method để minh họa cách chọn.

Nếu mục tiêu là sử dụng **tất cả** handlers, một bean khác có thể nhận `List<GreetingHandler>` hoặc `Map<String, GreetingHandler>` qua dependency injection. Khi cần thứ tự, cấu hình metadata `@Order`/`Ordered` hoặc policy tương đương và kiểm thử. Không mong registrar “đăng ký trước” nghĩa là thứ tự nghiệp vụ chắc chắn được bảo toàn.

### Bean role không giải quyết ambiguity

`definition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE)` chỉ đánh dấu vai trò infrastructure. Nó không tự đánh dấu primary hoặc tạo qualifier. Muốn chọn ứng viên đơn lẻ, phải dùng metadata/logic selection phù hợp như `@Primary`, qualifier, autowire candidate flag hoặc interface cụ thể.

### Aliases không phải definitions mới

BeanDefinitionRegistry/BeanFactory hỗ trợ tên và aliases theo hợp đồng. Nếu cần tương thích tên cũ, có thể đăng ký alias phù hợp, nhưng **alias không tăng số lượng bean definitions**. Nhiều tên cho một bean khác với hai definitions có cùng class.

## 18. Kiểm thử registrar có hiệu lực thật sự

Một lỗi dễ mắc là chỉ test:

```java
new GreetingRegistrar();
```

Lệnh này không có `AnnotationMetadata` thực tế, không có `BeanDefinitionRegistry` của context và không chạy Spring configuration processing. Nó không chứng minh `@EnableGreetings` có hiệu lực.

### Test mức context

Trong chương trình `RegistrarDemo`, có thể thêm:

```java
// Đoạn bổ sung bên trong try có context.
if (!context.containsBeanDefinition("greeting.email")) {
    throw new AssertionError("Registrar did not register email");
}
if (!context.containsBeanDefinition("greeting.sms")) {
    throw new AssertionError("Registrar did not register sms");
}
if (context.getBeanNamesForType(GreetingHandler.class).length != 2) {
    throw new AssertionError("Unexpected GreetingHandler bean count");
}
if (!"email:Hello, Lan".equals(
        context.getBean("greeting.email", GreetingHandler.class).greet("Lan"))) {
    throw new AssertionError("Wrong constructor argument");
}
```

Các assertions không in thêm stdout khi đúng. Chúng kiểm tra các tầng:

1. Registrar thực sự được Spring gọi thông qua `@Import`.
2. Bean definitions được ghi vào registry.
3. BeanFactory tạo đúng instances theo type.
4. Constructor argument của từng bean được dùng như mong muốn.

### Test trường hợp xấu

Nên thêm các kịch bản riêng:

| Cấu hình | Kết quả nên kiểm tra |
| --- | --- |
| `@EnableGreetings({"email","sms"})` | Hai definitions và inject theo qualifier được |
| `@EnableGreetings({"email","email"})` | Fail fast, báo duplicate channel theo chính sách demo |
| `@EnableGreetings({"Email"})` | Fail fast theo regex lowercase của demo |
| Bean name `greeting.email` đã tồn tại trước khi registrar chạy | Không âm thầm override; kiểm tra lỗi hoặc policy đã thiết kế |
| Không import registrar và không khai báo handler | Không có bean handler |
| Chỉ import registrar trực tiếp nhưng không có `@EnableGreetings` | Báo lỗi rõ vì thiếu metadata |
| Bean class có constructor không tương thích arguments | Bean creation thất bại đúng gốc lỗi |
| Singleton vs prototype | Identity và lifecycle theo scope |
| Nhiều import chains | Số definitions và collision/idempotency đúng hợp đồng |

Trong integration test, dùng `AnnotationConfigApplicationContext` với những configuration classes được thiết kế riêng cho case cần test. Đừng sửa BeanFactory của một context đang phục vụ request để mô phỏng bootstrap nếu bài test chỉ cần xây một context mới.

### Test registrar ở cấp thấp

Có thể kiểm tra logic đăng ký metadata với registry giả/`DefaultListableBeanFactory`, nhưng vì custom annotations và `@Import` được Spring xử lý ở mức configuration class, **integration test với context thật** là cách tốt nhất để xác minh dây chuyền đầy đủ.

## 19. Cảnh báo về AOT, native image và động hóa quá mức

Khi dùng Spring AOT/native image, hành vi đăng ký bean definitions phải phù hợp với cách Spring phân tích cấu hình ở build-time và runtime. Việc quyết định bean class hoặc cấu trúc bean dựa trên dữ liệu runtime không được biết lúc build có thể đòi hỏi thiết kế AOT/hints/tùy chỉnh thích hợp.

Một registrar deterministic dựa trên constant annotation attributes như `@EnableGreetings({"email","sms"})` thường đơn giản hơn để phân tích so với việc:

- Tải class name từ mạng khi ứng dụng khởi động.
- Scan ngẫu nhiên mọi package theo dữ liệu bên ngoài.
- Thay đổi danh sách beans theo một kết quả API call runtime không kiểm soát.
- Sinh tên bean không ổn định từ thời gian hoặc random.
- Phụ thuộc dynamic proxy/reflection mà không cung cấp hints cần thiết cho native image.

**Đừng coi `ImportBeanDefinitionRegistrar` tự động bảo đảm tính tương thích native.** Nếu mục tiêu bao gồm Spring AOT, cần kiểm thử build/runtime tương ứng và xem tài liệu AOT của framework đúng phiên bản.

## 20. Hiệu năng và bảo mật khi registrar thêm nhiều definitions

Một registrar chạy sớm. Dù nó chỉ đăng ký metadata, việc tạo số lượng lớn beans ở refresh có thể ảnh hưởng bộ nhớ, startup và dependency graph.

Nên:

- Giữ thao tác trong registrar **nhanh và deterministic**.
- Giới hạn số lượng definitions theo yêu cầu rõ ràng.
- Không tải external data hoặc mạng từ registrar để quyết định dynamic beans, trừ khi đã có một hợp đồng bootstrap rất đặc biệt và được kiểm soát.
- Dùng tên có namespace/prefix rõ, ví dụ `greeting.email` thay vì `handler` quá chung.
- Validate annotation attributes, class names, resource paths và bean naming trước khi ghi registry.
- Không cho phép giá trị không tin cậy quyết định class tùy ý được instantiate nếu không có allowlist/kiểm soát.
- Tách logic cấu hình khỏi business side effects và kiểm tra lifecycle.
- Khi tạo beans chứa secrets/resources, để BeanFactory quản lý constructor injection và cleanup; không đẩy dữ liệu nhạy cảm vào bean names hoặc logs.

Có thể log số definitions được đăng ký ở mức phù hợp trong dev/test, nhưng tránh log toàn bộ configuration values nhạy cảm.

## 21. Những lỗi phổ biến và cách chẩn đoán

| Triệu chứng | Nguyên nhân thường gặp | Hướng xử lý |
| --- | --- | --- |
| Registrar không được gọi | Class cấu hình mang `@Import`/`@Enable...` chưa được Spring đăng ký | Kiểm tra root `@Configuration`, `@Import` và bootstrap context |
| Registrar được gọi nhưng không có bean | Override sai overload/rỗng; metadata null; không thực hiện `registerBeanDefinition` | Debug đúng interface method và registry |
| `getAnnotationAttributes(...)` trả null | Annotation nằm trên class khác, import trực tiếp registrar, hoặc tên annotation sai | Dùng metadata của importing class, validate và báo lỗi |
| `NoSuchBeanDefinitionException` | Bean name/type chưa đăng ký hoặc injection cần bean khác | Kiểm tra `containsBeanDefinition`, names, exposed types và qualifiers |
| `NoUniqueBeanDefinitionException` | Registrar tạo nhiều beans cùng type, injection yêu cầu một | Dùng qualifier/primary/collection đúng ý định |
| `BeanCreationException` / constructor mismatch | Constructor args không khớp bean class hoặc dependency thiếu | Sửa `BeanDefinitionBuilder` arguments và constructor |
| Bean name collision | Multiple registrars/configs tạo cùng tên | Fail fast hoặc policy override có tài liệu/test rõ |
| Registrar chạy lặp gây lỗi | Duplicate imports hoặc nhiều feature annotation khác nhau | Xác định idempotency/deduplication và kiểm thử import chains |
| Bean chưa khởi tạo khi registrar gọi | Registrar hoạt động ở definition phase | Không gọi business bean từ registrar; dùng lifecycle phù hợp |
| Bean role infrastructure nhưng vẫn autowire | Role không điều khiển candidate selection | Dùng autowire candidate/qualifier đúng cơ chế |
| `@Bean` Java không thấy handler | Registrar không được imported vào context hoặc tên qualifier sai | Tạo integration test với cấu hình thật |
| Trùng bean definition mặc dù đã check | Definition khác được đăng ký sau pre-check | Phối hợp với override policy và kiểm thử |
| Native image thiếu bean hoặc reflection metadata | Registration không phù hợp AOT/runtime metadata | Đánh giá AOT hints, deterministic registration và native tests |
| Register `BeanDefinitionRegistryPostProcessor` từ registrar bị lỗi lifecycle | Sai extension point | Dùng cơ chế đăng ký post-processor đúng vòng đời |
| Test pass chỉ khi component scan rộng | Registrar bị bỏ quên; scanner tình cờ tạo bean thay thế | Thu hẹp test context, xác minh bean definition source và names |

### Trình tự debug thực dụng

1. Xác nhận configuration class có trong `ApplicationContext`.
2. Xác nhận `@Import(Registrar.class)` tồn tại trực tiếp hoặc meta-present trên custom enable annotation.
3. Xác nhận đúng overload `registerBeanDefinitions(...)` được override và thực thi.
4. Kiểm tra `AnnotationMetadata` cho annotation keys/values dự kiến.
5. Kiểm tra tên định tạo, collision và điều kiện bỏ qua/fail fast.
6. Xem `BeanDefinition` có đúng class, scope, constructor args và role không.
7. Phân biệt **definition có** với **instance đã tạo**.
8. Test `getBean(...)` và injection theo type/qualifier trong context thật.
9. Đọc nguyên nhân gốc của BeanCreation/UnsatisfiedDependency exception.
10. Khi có vấn đề AOT/Boot, kiểm tra extension point và lifecycle của framework đúng phiên bản.

## 22. Bài tập có đáp án định hướng

### Bài 1 — Khi nào dùng registrar?

Bạn chỉ cần tạo `InvoiceService` với hai dependency cố định. Có cần `ImportBeanDefinitionRegistrar` không?

**Đáp án:** Thường không. `@Bean InvoiceService invoiceService(Repository r, Policy p)` hoặc `@Service`/constructor injection rõ và ít boilerplate hơn. Registrar hợp lý khi cần metadata registration động theo cấu hình/annotation.

### Bài 2 — @Import ở đâu?

Một class `AppConfig` muốn bật `GreetingRegistrar`. Có thể viết:

```java
@Configuration(proxyBeanMethods = false)
@Import(GreetingRegistrar.class)
class AppConfig { }
```

Nếu registrar của ví dụ chính đọc `@EnableGreetings`, đoạn này chạy đúng không?

**Đáp án:** Spring có thể import registrar, nhưng vì `AppConfig` không mang `@EnableGreetings`, logic demo sẽ phát hiện `attributes == null` và fail fast. Muốn ví dụ đó hoạt động, dùng `@EnableGreetings({"email","sms"})` hoặc đổi hợp đồng registrar để hỗ trợ import trực tiếp.

### Bài 3 — Tạo definition hay tạo instance?

`registry.registerBeanDefinition("x", def)` đã gọi constructor của bean class chưa?

**Đáp án:** Không mặc định. Registrar đăng ký metadata; BeanFactory tạo instance sau đó theo scope, eager/lazy initialization và dependency resolution.

### Bài 4 — Bean names

Với annotation `@EnableGreetings({"email","sms"})`, registrar demo tạo những bean names nào?

**Đáp án:** `greeting.email` và `greeting.sms`. Cả hai cùng class `GreetingHandler`, nhưng constructor arguments khác. Đó là hai bean definitions, không phải aliases.

### Bài 5 — Đọc AnnotationMetadata

Tại sao không dùng `GreetingRegistrar.class.getAnnotation(EnableGreetings.class)` để lấy channels?

**Đáp án:** Vì annotation được đặt trên importing configuration class `AppConfig`. Spring cung cấp `AnnotationMetadata` của class đó cho registrar. Metadata registrar class không phải nguồn cấu hình cần đọc.

### Bài 6 — Constructor injection

Để đăng ký bean `Worker` có constructor `Worker(String name)`, lựa chọn nào đúng về ý tưởng?

```java
BeanDefinition def = BeanDefinitionBuilder
        .genericBeanDefinition(Worker.class)
        .addConstructorArgValue("batch")
        .getBeanDefinition();
registry.registerBeanDefinition("worker.batch", def);
```

**Đáp án:** Hợp lý; đây là metadata constructor argument. `Worker` phải có constructor tương thích. Còn type `BeanDefinition` và registry phải thuộc Spring API.

### Bài 7 — Bean alias hay bean thứ hai?

Nếu `registerBeanDefinition("greeting.email", def)` rồi tạo thêm một alias `legacyGreeting` trỏ về `greeting.email`, bạn có hai definitions không?

**Đáp án:** Không. Alias chỉ là tên khác của cùng definition; số definitions vẫn là một. Nếu đăng ký **một definition thứ hai** dưới `greeting.legacy` thì mới có thêm bean definition.

### Bài 8 — BeanDefinitionRegistryPostProcessor

Có thể từ một registrar đăng ký bean implement `BeanDefinitionRegistryPostProcessor` để nó chạy như một post-processor mới không?

**Đáp án:** Không nên làm vậy; Javadoc registrar cảnh báo không đăng ký types này từ registrar do lifecycle constraints trong configuration processing. Chọn extension point/post-processor registration đúng.

### Bài 9 — Chọn implementation trong Java @Bean

Có hai `GreetingHandler` bean, `greeting.email` và `greeting.sms`. Một Java bean chỉ muốn email. Viết factory parameter như thế nào?

```java
@Bean
GreetingDashboard dashboard(
        @Qualifier("greeting.email") GreetingHandler handler) {
    return new GreetingDashboard(handler);
}
```

**Đáp án:** Dùng `@Qualifier("greeting.email")` để xác định ứng viên. Nếu chỉ ghi `GreetingHandler handler`, Spring có thể gặp nhập nhằng do có hai candidates cùng type.

### Bài 10 — Validation

Nếu `@EnableGreetings({"email","Email"})` trong demo, kết quả nên là gì?

**Đáp án:** Registrar validate regex `[a-z][a-z0-9-]*`, nên `Email` bị từ chối (chữ hoa). Đây là convention của demo, không phải luật tên bean bắt buộc của Spring.

### Bài 11 — Aware/constructor injection của registrar

Registrar cần `Environment` để quyết định bật một feature. Có cần tạo một Spring business bean chỉ để đọc một property không?

**Đáp án:** Không. Có thể dùng `EnvironmentAware` hoặc constructor có tham số `Environment` thuộc danh sách supported types cho registrar. Lấy giá trị ổn định rồi đăng ký definitions; tránh khởi tạo business bean sớm.

### Bài 12 — Thiết kế @EnableFeature

Bạn muốn annotation `@EnableDataAdapters({"mysql","postgres"})` tạo một bean adapter cho mỗi tên. Hãy nêu các thành phần chính.

**Đáp án định hướng:**

1. Khai báo `@interface EnableDataAdapters` với `@Target(TYPE)`, `@Retention(RUNTIME)` và `@Import(DataAdapterRegistrar.class)`.
2. Registrar đọc mảng names từ `AnnotationMetadata` của importing class.
3. Validate danh sách và tạo tên ổn định như `adapter.mysql`/`adapter.postgres`.
4. Tạo `BeanDefinition` mỗi adapter bằng builder, constructor args/metadata phù hợp.
5. Kiểm tra collision policy rồi `registerBeanDefinition` từng entry.
6. Viết integration test tạo `AnnotationConfigApplicationContext` để xác minh definitions, injection, wrong values và duplicate names.
7. Nếu adapter dùng DB thật, tránh kết nối ngay trong registrar; để BeanFactory và lifecycle phù hợp quản lý.

## 23. Checklist trước khi đưa registrar vào thư viện

- [ ] Có lý do thật sự phải tạo bean definitions động; `@Bean`/component scan không còn diễn đạt gọn hơn.
- [ ] Registrar được kết nối vào Spring configuration qua `@Import` trực tiếp hoặc custom `@Enable...`.
- [ ] Đã chọn đúng overload `registerBeanDefinitions` và không override làm mất default delegation ngoài ý định.
- [ ] Không instantiate class cấu hình hoặc business service để đọc annotation values; dùng `AnnotationMetadata`.
- [ ] Annotation attributes được validate trước khi đăng ký.
- [ ] Bean names deterministic, có namespace và collision policy rõ.
- [ ] `BeanDefinition` mô tả đúng class, constructor values, scope, role, autowire candidate và qualifiers nếu cần.
- [ ] Không dùng role metadata như thay thế `@Primary`/`@Qualifier`.
- [ ] Không tự khởi tạo resource, gọi external services hay tạo context con trong registrar.
- [ ] Không cố đăng ký `BeanDefinitionRegistryPostProcessor` từ registrar.
- [ ] Có test cho một/nhiều beans, duplicate names, invalid values và missing annotation.
- [ ] Test Spring context thật chứng minh type/qualifier injection hoạt động.
- [ ] Hiểu sự khác nhau giữa definition registration và instance creation.
- [ ] Với Spring AOT/native, đã đánh giá tính deterministic và metadata reflection/proxy cần thiết.
- [ ] Có tài liệu về cách ứng dụng bật/tắt module và tên public bean mà nó cung cấp.

## Tổng kết

`ImportBeanDefinitionRegistrar` là extension point cho **lớp hạ tầng cần đăng ký BeanDefinition theo metadata của configuration class**. Nó kết hợp với `@Import`, thường được đóng gói thành custom annotation kiểu `@Enable...`. Registrar đọc `AnnotationMetadata`, dựng definitions với `BeanDefinitionBuilder`/`RootBeanDefinition` và thêm chúng qua `BeanDefinitionRegistry`. Việc quản lý instance, scope, dependency injection và lifecycle **vẫn thuộc Spring BeanFactory**.

Hãy sử dụng registrar để diễn đạt **cấu trúc bean động ở startup** — không để chạy nghiệp vụ. Thiết kế tốt phải có tên bean ổn định, validate metadata, kiểm tra xung đột, không đụng vào những bean chưa khởi tạo và có integration tests chứng minh behavior. Đối với bean cố định, chọn `@Bean` hoặc stereotype thường đơn giản và bền vững hơn.

## Tài liệu chính thức

- [Spring Framework 6.2 — ImportBeanDefinitionRegistrar API](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/context/annotation/ImportBeanDefinitionRegistrar.html): hợp đồng registrar, các overload, Aware/constructor injection và cảnh báo về `BeanDefinitionRegistryPostProcessor`.
- [Spring Framework 6.2 — @Import API](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/context/annotation/Import.html): các loại imports được Spring hỗ trợ.
- [Spring Framework 6.2 — BeanDefinitionRegistry API](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/beans/factory/support/BeanDefinitionRegistry.html): đăng ký và kiểm tra definitions.
- [Spring Framework 6.2 — BeanDefinitionBuilder API](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/beans/factory/support/BeanDefinitionBuilder.html): xây definitions với constructor args, scope và property values.
- [Spring Framework 6.2 — AnnotationMetadata API](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/core/type/AnnotationMetadata.html): đọc annotation data không cần tạo instance.
- [Spring Framework 6.2 — Java-based Container Configuration](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java.html): `@Configuration`, `@Bean`, `@Import` và composition.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="configuration-composition-008">Thiết kế annotation bật tính năng theo mẫu @Enable</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết kế annotation bật tính năng theo mẫu `@Enable` trong Spring Framework

## 1. Từ nhiều cấu hình rời rạc đến một annotation công khai

Trong Spring, một tính năng thường không chỉ có một object. Ví dụ audit cần một `AuditSink`, một service sử dụng sink, có thể thêm validator, advisor hoặc bean infrastructure. Nếu yêu cầu người dùng tự nhập từng cấu hình:

```java
@Configuration
@Import({
    AuditSinkConfig.class,
    AuditServiceConfig.class,
    AuditInfrastructureConfig.class
})
class AppConfig {
}
```

thì nơi khởi động phải biết quá nhiều chi tiết triển khai. Khi tính năng mở rộng, danh sách class có thể thay đổi và ứng dụng sử dụng thư viện phải sửa code chỉ để bổ sung một config nội bộ.

Một API gọn hơn:

```java
@Configuration
@EnableAudit(level = AuditLevel.DETAILED)
class AppConfig {
}
```

Trong thiết kế này, **`@EnableAudit` là annotation tự định nghĩa**, không có sẵn trong Spring Framework. Bên trong nó sử dụng `@Import` để dẫn Spring đến một class cấu hình, `ImportSelector` hoặc `ImportBeanDefinitionRegistrar`. Ứng dụng chỉ công bố **ý định bật tính năng**, còn thư viện giữ quyền tổ chức các beans và implementation.

Đó là mẫu thiết kế `@Enable...`: một **annotation ở type level** trở thành entry point khai báo cho một capability của ứng dụng.

Nhiều annotation thực tế của Spring đi theo hướng này: `@EnableCaching`, `@EnableAsync`, `@EnableScheduling`, `@EnableTransactionManagement`. Chúng không đồng nhất về implementation, nhưng cùng tư tưởng: kích hoạt cơ chế cấu hình hạ tầng qua annotation trên class cấu hình.

Mục tiêu bài học:

1. Thiết kế metadata của custom `@Enable...`: `@Target`, `@Retention`, `@Documented`, `@Import` và attributes.
2. Chọn đúng extension point: import class cố định, `ImportSelector`, `DeferredImportSelector` hay `ImportBeanDefinitionRegistrar`.
3. Bảo đảm tính năng được bật/tắt **đồng bộ cả producer và consumer beans**.
4. Hiểu lúc nào selector đọc annotation attributes, môi trường hoặc điều kiện.
5. Phân biệt bật khả năng framework với việc tạo mọi dependency hạ tầng cần thiết.
6. Thiết kế public API ổn định, tương thích refactor và không quét quá rộng.
7. Chạy demo Java 17+ với Spring 6.2 cho `@EnableAudit` ở các chế độ BASIC, DETAILED, OFF, đối chiếu đầu ra thực tế dự kiến.
8. Kiểm thử và chẩn đoán lỗi thiếu bean, trùng bean, proxy và lifecycle.

## 2. Một annotation `@Enable...` hoạt động như thế nào?

Về mặt Java, annotation chỉ là **metadata**; tự gắn `@EnableAudit` lên class không có nghĩa JVM gọi code tạo bean. Spring phải nhìn thấy configuration class đó, xử lý metadata và có một cơ chế chuyển metadata thành bean definitions.

Ví dụ mô hình phổ biến:

```text
AppConfig được đăng ký vào ApplicationContext
                    |
                    v
         @EnableAudit(level=DETAILED)
                    |
                    v
     Annotation được meta-annotated @Import
                    |
                    v
           AuditConfigurationSelector
                    |
          Đọc metadata/attributes
                    |
                    v
  Chọn DetailedAuditConfig + AuditServiceConfig
                    |
                    v
    Spring xử lý imported configuration classes
                    |
                    v
  Đăng ký bean definitions AuditSink, AuditService
                    |
                    v
      BeanFactory resolve dependency & lifecycle
                    |
                    v
        Các managed beans sẵn sàng sử dụng
```

**Điểm cốt lõi:** annotation công khai **khai báo yêu cầu**, extension point nội bộ **chọn/đăng ký definitions**, BeanFactory **tạo và quản lý objects**. Không gộp ba giai đoạn vào một method tự gọi `new` rồi cho rằng container đã quản lý object.

### 2.1. Điều kiện đầu tiên: class dùng `@Enable...` phải được xử lý

```java
@EnableAudit
class AppConfig {
}
```

Nếu `AppConfig` không được đưa vào Spring context bằng đăng ký trực tiếp, component scan, một `@Import` khác hay cơ chế hợp lệ tương ứng, annotation sẽ **không được xử lý**. Trong demo chạy được, chúng ta sử dụng:

```java
new AnnotationConfigApplicationContext(BasicAppConfig.class)
```

Đây là snippet minh họa từ ví dụ ở mục 8. Bean definitions được tạo trong context đó, không phải trên toàn JVM.

### 2.2. Bật tính năng khác với bật component scanning

`@EnableAudit` không bắt buộc phải có `@ComponentScan`. Khi một annotation khai báo `@Import(AuditSelector.class)`, Spring chỉ nhập các class mà selector chỉ ra, và những registrations phát sinh từ các class đó.

Nếu imported configuration tự chứa `@ComponentScan`, nó có thể quét một package và đăng ký thêm nhiều bean; nhưng đó là do **cấu hình đã nhập**, không phải vì mọi annotation `@Enable...` tự nhiên quét package của thư viện.

### 2.3. Một khả năng có thể gồm nhiều beans

“Bật audit” thường cần cả **writer** và **service sử dụng writer**. Nếu chỉ đăng ký service mà không cung cấp writer khi dependency bắt buộc, context có thể thất bại. Nếu tắt writer nhưng vẫn để service được tạo eager và đòi writer, tắt tính năng cũng có thể khiến context lỗi.

Do đó, hãy nghĩ theo **module bean graph** chứ không chỉ theo một class: selector phải chọn một tập configuration classes nhất quán. Khi disabled, hoặc không đăng ký consumer, hoặc consumer dùng optional dependency/fallback có chủ đích.

## 3. Cấu tạo tối thiểu của custom `@Enable` annotation

Một annotation thường có dạng:

```java
package com.acme.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(AuditModuleConfig.class)
public @interface EnableAudit {
}
```

Đây là **file minh họa `EnableAudit.java`**, cần thêm class `AuditModuleConfig` hợp lệ để biên dịch. Trong phiên bản tối giản này, `@Import` đưa vào một configuration class cố định; không cần viết selector hoặc registrar.

Ý nghĩa từng metadata:

| Annotation | Vai trò |
| --- | --- |
| `@Target(ElementType.TYPE)` | Chỉ cho phép gắn annotation vào class/interface/annotation type theo khả năng Java |
| `@Retention(RetentionPolicy.RUNTIME)` | Giữ annotation ở runtime để framework có thể truy xuất metadata |
| `@Documented` | Cho phép Javadoc của type sử dụng annotation trình bày annotation ấy |
| `@Import(AuditModuleConfig.class)` | Chỉ Spring đến class cấu hình hoặc extension point cần xử lý |
| `public @interface EnableAudit` | Định nghĩa một Java annotation được application có thể dùng |

`@Retention(RUNTIME)` là lựa chọn chuẩn cho annotation cấu hình dễ đọc qua reflection. Spring cũng có metadata readers dựa trên class files, nhưng không nên vì vậy dùng một retention mơ hồ cho public configuration annotation.

### 3.1. Phiên bản không có tham số

Nếu luôn chỉ có một cách triển khai audit, sử dụng cấu hình tĩnh:

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(AuditModuleConfig.class)
public @interface EnableAudit {
}
```

Và application:

```java
@Configuration(proxyBeanMethods = false)
@EnableAudit
class ShopConfiguration {
}
```

Đây là hai trích đoạn minh họa riêng. Ưu điểm: ít mã, dễ debug, imported config định nghĩa rõ các beans. Nếu logic phụ thuộc profiles/properties nhưng vẫn chỉ là điều kiện có/không, có thể đặt `@Conditional` trên configuration hoặc bean factory methods mà không cần một selector.

### 3.2. Phiên bản có thuộc tính

Khi muốn cung cấp lựa chọn BASIC/DETAILED hoặc cho phép disable:

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(AuditSelector.class)
public @interface EnableAudit {
    boolean enabled() default true;
    AuditLevel level() default AuditLevel.BASIC;
}
```

Đây là **trích đoạn minh họa** cần `AuditSelector` và enum `AuditLevel`. Những thuộc tính trên trở thành **public API của annotation**; người dùng có thể viết:

```java
@EnableAudit(enabled = true, level = AuditLevel.DETAILED)
```

Hoặc:

```java
@EnableAudit(enabled = false)
```

Đừng chọn mặc định tùy tiện: nếu bật audit mặc định có chi phí hoặc nghĩa vụ tuân thủ, phải thống nhất ý nghĩa của `enabled=true`, môi trường hỗ trợ, các dependency cần cấu hình và hành vi khi thiếu provider.

### 3.3. Có cần `@AliasFor` không?

**Không phải annotation `@Enable...` nào cũng cần `@AliasFor`.** Với `enabled()` và `level()` là attributes riêng do selector đọc, không cần alias.

`@AliasFor` hữu ích khi custom annotation **tái xuất** hoặc đồng bộ attributes của một meta-annotation tương ứng. Ví dụ khi tạo composed annotation trực tiếp trên `@ComponentScan`, việc alias sang `basePackages` phải được khai báo đúng semantics, có kiểm thử và tài liệu rõ ràng.

Không sử dụng `@AliasFor` để nói rằng “`enabled` tự động truyền đến mọi imported configuration”. `@Import` không có thuộc tính `enabled` để alias; selector hoặc condition mới là nơi quyết định cách xử lý.

## 4. Ba cách triển khai annotation bật tính năng

### 4.1. Cách A — `@Import` class cấu hình cố định

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(AuditModuleConfig.class)
public @interface EnableAudit {
}
```

Phù hợp khi:

- một module luôn có cùng tập bean definitions;
- không cần chọn implementation dựa trên annotation attributes;
- muốn cấu trúc tối giản, dễ tìm từ annotation đến configuration.

`AuditModuleConfig` có thể khai báo nhiều `@Bean` methods, hoặc import thêm các config khác. Khi được xử lý, bean definitions phát sinh vẫn chịu mọi quy tắc về scope, dependency, conditions và names.

### 4.2. Cách B — `@Import(ImportSelector.class)`

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(AuditSelector.class)
public @interface EnableAudit {
    AuditLevel level() default AuditLevel.BASIC;
}
```

`AuditSelector` implement `org.springframework.context.annotation.ImportSelector`:

```java
@Override
public String[] selectImports(AnnotationMetadata metadata) {
    Map<String, Object> attrs =
            metadata.getAnnotationAttributes(EnableAudit.class.getName());
    if (attrs == null) {
        throw new IllegalStateException("Missing @EnableAudit");
    }
    AuditLevel level = (AuditLevel) attrs.get("level");
    return level == AuditLevel.DETAILED
            ? new String[] { DetailedAuditConfig.class.getName() }
            : new String[] { BasicAuditConfig.class.getName() };
}
```

Các snippet ở trên là **minh họa**, cần đủ imports, enum, classes. `ImportSelector` trả **tên class đầy đủ**, không trả bean name và không trực tiếp trả object bean.

Phù hợp khi:

- attributes của `@Enable...` quyết định class nào cần import;
- có nhiều implementation configurations;
- muốn ẩn chi tiết packages/classes nội bộ sau annotation;
- lựa chọn xảy ra lúc xây context và có thể kiểm thử rõ ràng.

`ImportSelector` có thể đọc `Environment` qua `EnvironmentAware` hoặc constructor injection các loại hạ tầng được hỗ trợ theo Javadoc Spring. Nhưng không nên lookup eager business beans chỉ để chọn import: selection diễn ra trong giai đoạn configuration parsing, thường trước khi bean ứng dụng sẵn sàng.

### 4.3. Cách C — `@Import(ImportBeanDefinitionRegistrar.class)`

Một registrar có thể sử dụng `AnnotationMetadata` và `BeanDefinitionRegistry` để chủ động tạo bean definitions:

```java
public final class AuditRegistrar
        implements ImportBeanDefinitionRegistrar {

    @Override
    public void registerBeanDefinitions(
            AnnotationMetadata metadata,
            BeanDefinitionRegistry registry) {
        RootBeanDefinition definition =
                new RootBeanDefinition(AuditRuntime.class);
        registry.registerBeanDefinition("auditRuntime", definition);
    }
}
```

Đây là **đoạn minh họa**, cần imports từ `org.springframework.context.annotation.ImportBeanDefinitionRegistrar`, `org.springframework.core.type.AnnotationMetadata`, `org.springframework.beans.factory.support.BeanDefinitionRegistry`/`RootBeanDefinition` và một class `AuditRuntime` có constructor phù hợp.

Phù hợp khi:

- số lượng/type/names của beans phụ thuộc metadata động;
- framework cần đăng ký definitions ở cấp thấp hơn `@Bean`;
- bạn thực sự cần kiểm soát `BeanDefinitionRegistry`.

Không nên dùng registrar khi chỉ cần hai factory methods cố định. Nó phức tạp hơn selector/configuration, dễ gây xung đột tên và có giới hạn vòng đời của các infrastructure post-processors. Đặc biệt, Javadoc Spring cảnh báo về cách đăng ký `BeanDefinitionRegistryPostProcessor` qua registrar; hãy tuân theo extension point phù hợp.

### 4.4. So sánh lựa chọn

| Yêu cầu | Khuyến nghị đầu tiên |
| --- | --- |
| Bật một tập cấu hình cố định | `@Import(MyFeatureConfig.class)` |
| Có hai config, chọn bằng attribute `mode` | `ImportSelector` |
| Chỉ cần bật/tắt một config theo property | `@Conditional` trên config có thể đủ |
| Môi trường dev/prod | `@Profile` nếu phù hợp |
| Thêm nhiều bean definitions động theo metadata | `ImportBeanDefinitionRegistrar` |
| Auto-configuration có ordering/aggregation phức tạp | Xem `DeferredImportSelector` và cơ chế auto-config tương ứng |
| Phát hiện tất cả application components dưới một package | `@ComponentScan`, không lạm dụng `@Import` |

Nguyên tắc: **chọn mức trừu tượng tối thiểu đáp ứng đúng yêu cầu**.

## 5. Phân biệt `@Enable...` với các annotation thực tế của Spring

Spring Framework có một số annotation tiêu biểu. Hãy xem cách sử dụng và cơ chế kích hoạt của chúng để thiết kế annotation riêng có trách nhiệm rõ ràng.

| Annotation | Bật tính năng gì? | Điều cần có thêm |
| --- | --- | --- |
| `@EnableCaching` | Hạ tầng xử lý các annotation cache như `@Cacheable` | `CacheManager`/cache resolver thích hợp và caching configuration |
| `@EnableAsync` | Hạ tầng xử lý các method `@Async` | Các managed beans phù hợp và executor/configuration thích hợp |
| `@EnableScheduling` | Hạ tầng phát hiện/đăng ký `@Scheduled` methods | Managed beans với scheduled methods và scheduler phù hợp |
| `@EnableTransactionManagement` | Hạ tầng xử lý `@Transactional` | Transaction manager phù hợp với tài nguyên và application |
| Custom `@EnableAudit` | Module audit do chính ứng dụng/thư viện định nghĩa | Config/beans được selector hoặc registrar đăng ký đúng |

### 5.1. Ví dụ thật: `@EnableCaching`

Trong Spring Framework 6.2, `@EnableCaching` được meta-annotated bằng `@Import(CachingConfigurationSelector.class)`. Selector này lựa chọn hạ tầng phù hợp với `AdviceMode`/cấu hình. Đây là ví dụ điển hình cho thiết kế “annotation công khai → selector → infrastructure configurations”.

Nhưng chỉ viết `@EnableCaching` **không bảo đảm mọi method được cache**. Cần có các cache annotations trên managed beans, manager/resolver phù hợp và cơ chế proxy/AspectJ theo config. Với proxy mode, self-invocation thông thường có thể bypass proxy; bài toán này thuộc AOP, không phải cơ chế `@Enable` tự giải quyết.

### 5.2. Ví dụ thật: `@EnableAsync`

`@EnableAsync` sử dụng `AsyncConfigurationSelector` để nhập cấu hình cho asynchronous method execution. Nó không biến mọi method Java thành bất đồng bộ. Cần method được đánh dấu `@Async` trên Spring-managed bean và lời gọi đi qua đúng infrastructure/proxy theo mode.

Không suy ra rằng một custom `@EnableAudit` phải làm AOP để trở thành “@Enable đúng chuẩn”. Nếu audit chỉ cần producer/service beans, `@Import` hoặc selector đơn giản là hoàn toàn hợp lệ.

### 5.3. Ví dụ thật: `@EnableScheduling`

`@EnableScheduling` đăng ký những infrastructure beans thích hợp để phát hiện `@Scheduled` trên managed beans. Nó không đồng nghĩa tự tạo jobs nếu ứng dụng không có method phù hợp. Việc scheduler, thread pool, lỗi thực thi và lifecycle chạy job cần được cân nhắc riêng.

### 5.4. Ví dụ thật: `@EnableTransactionManagement`

Annotation này bật hạ tầng hỗ trợ `@Transactional` nhưng **không tự tạo database**, `DataSource` hay transaction manager cho mọi ứng dụng. Một custom `@Enable...` cũng vậy: bạn cần công bố rõ những beans/tài nguyên phải được người dùng cung cấp và lúc nào tính năng thực sự sẵn sàng.

## 6. Public API: thiết kế attributes có ý nghĩa

Annotation bật tính năng nên có surface area nhỏ, rõ và ổn định.

### 6.1. `enabled`, `mode`, `level` và giá trị mặc định

Một thiết kế tốt:

```java
public @interface EnableAudit {
    boolean enabled() default true;
    AuditLevel level() default AuditLevel.BASIC;
}
```

Đây là đoạn minh họa thuộc tính, chưa bao gồm `@Target`/`@Retention`/`@Import`.

- `enabled` thể hiện **bật/tắt toàn bộ module**.
- `level` chọn implementation/behavior khi module được bật.
- `default` đảm bảo cách gọi `@EnableAudit` không tham số có semantics xác định.
- Khi `enabled=false`, các thuộc tính khác có thể bị bỏ qua hoặc phải được validate theo policy được tài liệu hóa.

Nếu use case là chọn một trong hai protocols, tên attribute `protocol` có thể rõ hơn một `mode` chung chung. Không thêm hàng loạt switches cho từng internal bean chỉ vì annotation cho phép khai báo nhiều attributes.

### 6.2. Vì sao dùng enum thay vì String?

`AuditLevel.BASIC` và `AuditLevel.DETAILED` được Java compiler kiểm tra; typo như `DETAILD` là compile error. Ngược lại, `String level()` dễ đưa typo đến runtime.

Enum phù hợp với tập lựa chọn nhỏ, ổn định. Khi cần mở rộng provider bởi plugin bên thứ ba, có thể cần model khác: class token, provider type hoặc configuration properties. Đừng cố liệt kê mọi extension tương lai trong một enum public nếu ecosystem dự kiến mở.

### 6.3. Không đặt secrets vào annotation attributes

Java annotation attributes về bản chất là metadata tĩnh của class. Không dùng annotation để chứa mật khẩu, API key hoặc secrets. Những giá trị có thể thay đổi theo môi trường nên đi qua cấu hình runtime/secret management phù hợp, không compile vào source.

### 6.4. Cẩn trọng với rename/refactor

Class selector và config nên dùng `SomeConfig.class.getName()` thay vì chuỗi tên class literal để giảm rủi ro sai package, nhất là với nested classes. Đổi tên public annotation hoặc enum constants là thay đổi API ảnh hưởng mọi caller.

Bean names mà consumer tham chiếu bằng string cũng có thể là hợp đồng, nhưng chúng không cần được lộ như attributes nếu người dùng chỉ cần interface/type injection.

### 6.5. Những gì `@Enable...` không nên cam kết

Đừng quảng cáo rằng annotation “bật audit cho **tất cả** objects” khi thực tế module chỉ cấu hình một `AuditService`. Những object tạo bằng `new` ngoài Spring không tự được quản lý. Nếu feature dùng AOP, caller cần đi qua proxy phù hợp; nếu dùng bean factory, chỉ các consumers được inject service mới tận dụng capability.

Hãy mô tả chính xác:
- Bean definitions nào được đăng ký?
- Cần những dependencies nào?
- Tính năng bị disable nghĩa là gì?
- Có tác động đến những bean cũ khi context đã refresh hay không?
- Có bean scope/lifecycle, side effects hoặc startup cost đáng kể nào không?

## 7. Vòng đời và thời điểm bật tính năng

Một số mốc quan trọng:

```text
Class được đăng ký vào context
           |
           v
Configuration metadata được đọc
           |
           v
@Import / selector / registrar xử lý
           |
           v
Bean definitions được đăng ký
           |
           v
Các bean post-processors và factory phases vận hành
           |
           v
Beans được tạo khi cần theo scope/lazy/dependencies
           |
           v
Ứng dụng bắt đầu sử dụng capability
```

### 7.1. `@Enable` thường là cấu hình bootstrap, không phải runtime toggle

Nếu `@EnableAudit(enabled=true)` dẫn selector đến `AuditServiceConfig`, lựa chọn được áp dụng trong quá trình xây context. Sau `refresh()`, việc sửa một property ngẫu nhiên **không tự chạy lại** `ImportSelector` để xóa hoặc thêm bean definitions.

Muốn bật/tắt audit trong khi ứng dụng đang chạy, thường cần một facade luôn tồn tại, chẳng hạn `AuditService` kiểm tra một feature flag tại mỗi invocation, hoặc một mechanism refresh/rebuild context được thiết kế đúng. Đó là yêu cầu khác với mẫu `@Enable...` tĩnh.

### 7.2. Scope/lazy vẫn thuộc BeanFactory

`@Enable...` tự nó không thay đổi singleton thành prototype, không bảo đảm các beans được lazy và không quyết định thứ tự khởi tạo. Mọi bean được import vẫn dùng `@Scope`, `@Lazy`, `@DependsOn`, dependency graph và BeanPostProcessors như những beans khác.

### 7.3. “Enable” không có nghĩa “bỏ qua mọi điều kiện khác”

Imported `@Configuration` có thể được gắn `@Profile`/`@Conditional`. Nếu điều kiện không thỏa, bean definitions có thể bị bỏ qua dù selector đã trả class name. Consumer yêu cầu dependency bắt buộc vẫn có thể làm context fail. Phải thiết kế đồng bộ cả consumer config và producer config.

### 7.4. Điều kiện classpath

Nếu selector trả `com.vendor.AuditIntegrationConfig` nhưng module chứa class này không có trên runtime classpath, import processing có thể thất bại. Đừng trả tên của class tùy chọn khi chưa kiểm tra điều kiện tương ứng; với Spring Boot libraries, các conditional auto-config mechanisms thường phù hợp hơn để tích hợp classpath-conditional modules.

## 8. Ví dụ chạy được Java 17+: thiết kế `@EnableAudit` đầy đủ

Chúng ta tạo một Java application Spring Context thuần với ba configuration classes:

- `BasicApp`: `@EnableAudit` mặc định, chọn BASIC.
- `DetailedApp`: `@EnableAudit(level=DETAILED)`, chọn DETAILED.
- `OffApp`: `@EnableAudit(enabled=false)`, không đăng ký các beans của module audit.

Mỗi context độc lập. `AuditImportSelector` chọn **hai cấu hình** khi enabled: một cung cấp `AuditSink`, một cung cấp `AuditService` phụ thuộc vào sink. Khi disabled, nó trả một mảng rỗng, nhờ đó không để lại consumer thiếu dependency.

Không cần Spring Boot, HTTP, database, XML hoặc external services.

### 8.1. Cấu trúc file

```text
enable-audit-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/
    └── EnableAuditDemo.java
```

File `settings.gradle`:

```groovy
rootProject.name = 'enable-audit-demo'
```

File `build.gradle`:

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
    mainClass = 'EnableAuditDemo'
}
```

Chạy bằng JDK 17+ và bản Gradle tương thích JDK. `spring-context` kéo theo các module Spring Beans/Core cần cho `@Configuration`, `@Import`, `AnnotationMetadata` và `AnnotationConfigApplicationContext`.

### 8.2. Mã nguồn hoàn chỉnh

Lưu toàn bộ vào `src/main/java/EnableAuditDemo.java`:

```java
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;

public class EnableAuditDemo {

    public enum AuditLevel {
        BASIC, DETAILED
    }

    public interface AuditSink {
        String format(String action);
    }

    public record BasicAuditSink() implements AuditSink {
        @Override
        public String format(String action) {
            return "basic:" + action;
        }
    }

    public record DetailedAuditSink() implements AuditSink {
        @Override
        public String format(String action) {
            return "detailed:" + action + "|trace";
        }
    }

    public record AuditService(AuditSink sink) {
        public String report(String action) {
            return sink.format(action);
        }
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @Import(AuditImportSelector.class)
    public @interface EnableAudit {
        boolean enabled() default true;
        AuditLevel level() default AuditLevel.BASIC;
    }

    public static class AuditImportSelector implements ImportSelector {
        @Override
        public String[] selectImports(
                AnnotationMetadata importingClassMetadata) {

            Map<String, Object> attributes =
                    importingClassMetadata.getAnnotationAttributes(
                            EnableAudit.class.getName());

            if (attributes == null) {
                throw new IllegalStateException(
                        "Missing @EnableAudit metadata");
            }

            boolean enabled = (Boolean) attributes.get("enabled");
            if (!enabled) {
                return new String[0];
            }

            AuditLevel level =
                    (AuditLevel) attributes.get("level");

            Class<?> sinkConfig = level == AuditLevel.DETAILED
                    ? DetailedAuditConfig.class
                    : BasicAuditConfig.class;

            return new String[] {
                    sinkConfig.getName(),
                    AuditServiceConfig.class.getName()
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class BasicAuditConfig {
        @Bean
        AuditSink auditSink() {
            return new BasicAuditSink();
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class DetailedAuditConfig {
        @Bean
        AuditSink auditSink() {
            return new DetailedAuditSink();
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AuditServiceConfig {
        @Bean
        AuditService auditService(AuditSink sink) {
            return new AuditService(sink);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAudit
    public static class BasicApp {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAudit(level = AuditLevel.DETAILED)
    public static class DetailedApp {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAudit(enabled = false)
    public static class OffApp {
    }

    private static void show(
            String label, Class<?> applicationConfig) {

        try (var context =
                     new AnnotationConfigApplicationContext(
                             applicationConfig)) {

            int sinkCount =
                    context.getBeanNamesForType(AuditSink.class).length;
            int serviceCount =
                    context.getBeanNamesForType(AuditService.class).length;

            System.out.println(label + ": sinks=" + sinkCount +
                    ", services=" + serviceCount);

            if (serviceCount > 0) {
                AuditService service =
                        context.getBean(AuditService.class);
                System.out.println(label + ": " +
                        service.report("checkout"));
            }
        }
    }

    public static void main(String[] args) {
        show("BASIC", BasicApp.class);
        show("DETAILED", DetailedApp.class);
        show("OFF", OffApp.class);
    }
}
```

### 8.3. Cách chạy và stdout

Tại thư mục `enable-audit-demo`:

```bash
gradle run --quiet
```

Nếu dự án có Gradle Wrapper, dùng `./gradlew run --quiet` trên Linux/macOS, hoặc `.\gradlew.bat run --quiet` trên Windows PowerShell.

Stdout **do chính chương trình in ra**:

```text
BASIC: sinks=1, services=1
BASIC: basic:checkout
DETAILED: sinks=1, services=1
DETAILED: detailed:checkout|trace
OFF: sinks=0, services=0
```

Đây là các dòng `System.out.println` từ demo. Một build tool hoặc logging backend có thể tạo thêm dòng riêng; không coi chúng là output nghiệp vụ của ứng dụng.

### 8.4. Phân tích từng case

**BASIC:** `@EnableAudit` trên `BasicApp` sử dụng defaults `enabled=true` và `level=BASIC`. Selector nhận metadata, chọn `BasicAuditConfig` và `AuditServiceConfig`, tạo bean `AuditSink` trả về `BasicAuditSink` và bean `AuditService` tiêu thụ sink đó. Có đúng một bean mỗi kiểu. `report("checkout")` trả chuỗi `basic:checkout`.

**DETAILED:** `DetailedApp` khai báo `level=DETAILED` nên selector chọn `DetailedAuditConfig`. Bean `AuditService` được tạo từ cùng `AuditServiceConfig`, nhận `DetailedAuditSink` theo interface `AuditSink`. Dòng audit chứa hậu tố `|trace`.

**OFF:** `OffApp` vẫn được Spring xử lý như một configuration class hợp lệ, nhưng selector đọc `enabled=false` và trả mảng rỗng. **Cả `AuditSink` lẫn `AuditService` đều không được đăng ký**, vì cả hai nguồn config nằm phía sau selector. Context refresh thành công; chương trình không gọi `getBean(AuditService.class)` ở nhánh không có service.

Cách thiết kế này làm cho “disable feature” có semantics rõ: không có audit capability beans trong context ấy. Nếu muốn service audit vẫn tồn tại nhưng là no-op, đó là một policy khác: hãy cho selector chọn `NoOpAuditConfig` đồng thời cung cấp đủ consumer dependencies.

### 8.5. Vì sao hai configs dùng chung bean name `auditSink` nhưng không xung đột?

`BasicAuditConfig` và `DetailedAuditConfig` đều có factory method `@Bean auditSink()`. Tuy nhiên, selector **chỉ chọn một trong hai configs tại một thời điểm**, còn mỗi case tạo **ApplicationContext riêng**. Vì vậy registry không nhận đồng thời hai bean definitions `auditSink`.

Nếu code khác lại import cả hai configs, tên `auditSink` có thể xung đột/được override tùy registry policy; sau khi đổi tên, cả hai vẫn có thể gây ambiguity khi inject `AuditSink` đơn. Selector không thể ngăn registrations độc lập do những module khác thực hiện.

### 8.6. Vì sao `AuditServiceConfig` dùng method parameter?

`AuditServiceConfig.auditService(AuditSink sink)` nhận dependency do Spring resolve. Nó không tự gọi `BasicAuditConfig.auditSink()` hoặc `DetailedAuditConfig.auditSink()` và không cần biết implementation đang hoạt động.

Điều này giữ cấu hình tách lớp: selector chọn provider config, BeanFactory inject đúng provider vào consumer. Khi thay thế implementation, consumer config không đổi.

### 8.7. Vì sao không dùng `@ComponentScan` trong ví dụ?

Để minh họa rõ “chỉ những classes được import mới tham gia”, demo không quét package. Nếu bật scanner trên package chứa các configs nhưng gắn thêm stereotype lên tất cả chúng, rất dễ vô tình đăng ký cả BASIC và DETAILED trước khi selector kịp tạo lựa chọn, làm sai mục tiêu demo.

Một thư viện có thể dùng component scanning nội bộ, nhưng khi thiết kế `@Enable...` có trách nhiệm chọn một implementation, hãy cân nhắc **package và registration boundaries**. Đừng scan toàn bộ thư viện chỉ để sử dụng đúng một bean.

## 9. Các biến thể thiết kế thường gặp

### 9.1. Bật tính năng nhưng cho phép không có provider: no-op

Nếu audit là optional và application luôn inject `AuditService`, tắt bằng cách **không đăng ký service** sẽ gây lỗi nếu consumer vẫn bắt buộc cần nó. Một chiến lược khác là luôn đăng ký interface service và đưa vào một `NoOpAuditSink` khi disabled.

Ví dụ minh họa:

```java
public record NoOpAuditSink() implements AuditSink {
    @Override
    public String format(String action) {
        return "ignored";
    }
}
```

Đoạn này cần interface `AuditSink` ở demo mục 8. Một selector có thể chọn `NoOpAuditConfig` + `AuditServiceConfig` khi `enabled=false`. Hợp đồng public khi đó đổi thành “audit service luôn có mặt nhưng có thể không ghi”. Chọn thiết kế dựa trên nhu cầu của consumers và yêu cầu an toàn/tuân thủ; không âm thầm bỏ audit có ý nghĩa bắt buộc trong hệ thống thật.

### 9.2. Tách `enabled` khỏi `level`

`enabled=false` thường ưu tiên hơn `level` vì không có gì để lựa chọn khi module tắt. Tuy nhiên, nếu muốn báo lỗi khi người dùng đặt `enabled=false, level=DETAILED`, selector có thể validate tổ hợp đó. Quy tắc phải được mô tả rõ trong API; ví dụ mục 8 chủ động bỏ qua `level` khi disabled.

### 9.3. Chọn theo `Environment`

Có thể cho selector implement `EnvironmentAware` và đọc một property như `audit.level` để override annotation attribute. Đây là một biến thể phù hợp khi cấu hình triển khai phải được điều chỉnh mà không sửa code. Cần công bố thứ tự ưu tiên: attribute thắng hay property thắng, xử lý giá trị lỗi thế nào, và property cần được thiết lập **trước configuration processing**.

Dùng `@ConditionalOnProperty` trong Spring Boot hoặc `@Conditional` trong Spring Framework cho yêu cầu conditional đơn giản có thể ít code hơn một selector custom. Đừng tự viết một hệ thống condition/flags quá rộng khi framework đã có extension point đáp ứng.

### 9.4. Annotation trỏ tới class được chọn

Đối với hệ sinh thái nhiều provider mà enum không đủ linh hoạt, một thiết kế `Class<? extends AuditSink> implementation()` có thể cho phép caller chọn class. Nhưng điều đó lộ implementation type vào public API và vẫn cần cách đăng ký/DI phù hợp. Các object do người dùng chọn phải được Spring tạo chứ không tự `new` trong selector để tránh mất injection/lifecycle.

### 9.5. Annotation kích hoạt advisor/proxy

Một số `@Enable...` bật infrastructure `BeanPostProcessor`, `Advisor` hoặc proxy để áp dụng behavior lên các managed beans. Khi thiết kế kiểu này, hãy phân biệt:

- **Import/registration** tạo infrastructure definitions.
- **Post-processing/AOP** áp dụng behavior cho beans/calls phù hợp.
- **Method invocation** có đi qua proxy không (self-invocation)?
- **Số lượng proxy/post-processors** có bị nhân đôi khi import nhiều config không?

Không phải mọi annotation `@Enable...` đều phải proxy. Hãy chọn infrastructure theo khả năng cần hỗ trợ, không vì muốn bắt chước toàn bộ mã nguồn của `@EnableCaching`.

## 10. ImportSelector, DeferredImportSelector và ImportBeanDefinitionRegistrar

### 10.1. ImportSelector thường

`ImportSelector.selectImports(AnnotationMetadata)` chạy trong giai đoạn Spring phân tích cấu hình. Trả về `String[]` tên class cần import. Có thể chọn theo annotation attributes, `Environment` hoặc những metadata bootstrap khác; thích hợp với tình huống trong ví dụ.

**Không trả bean names** như `"auditService"`. Hãy trả class names bằng `AuditServiceConfig.class.getName()`. Với nested classes, JVM binary name chứa `$`; dùng `Class.getName()` để tránh ghép sai.

### 10.2. DeferredImportSelector

`DeferredImportSelector` là biến thể cho phép trì hoãn selection đến giai đoạn processing sau khi các configuration classes thông thường được xét. Đây là cơ chế bootstrap, **không phải runtime reload** hay asynchronous task. `DeferredImportSelector.Group` có thể phối hợp nhiều deferred selectors, hữu ích trong auto-configuration frameworks phức tạp.

Đối với một tính năng audit với BASIC/DETAILED, selector thường là đủ. Chỉ chọn deferred nếu cần ordering/aggregation đặc thù có lý do rõ.

### 10.3. ImportBeanDefinitionRegistrar

Registrar làm việc trực tiếp với bean definition registry. Nó cho phép tạo tên/kiểu/metadata bean không phụ thuộc cố định vào một `@Bean` method, nhưng phức tạp hơn và cần hiểu bean factory lifecycle.

Một `@Enable...` trong thư viện có thể dùng registrar khi cần đăng ký adapter/proxy factories theo tên interface được khai báo trên annotation. Đừng sử dụng registrar để né việc viết một configuration class rõ ràng.

### 10.4. Có dùng đồng thời các cơ chế không?

Có, một custom annotation có thể import một configuration class mà cấu hình đó lại import selector hoặc registrar; một selector cũng có thể chọn một configuration chứa các `@Bean` methods và conditions. Điều này tạo đồ thị import mạnh nhưng có thể rất khó debug.

Khuyến nghị giữ **một điểm vào, một nguyên tắc lựa chọn, một nguồn chịu trách nhiệm đăng ký mỗi bean**. Ghi lại import graph trong tài liệu và kiểm thử số bean definitions cuối cùng.

## 11. Tích hợp vào dự án nhiều module

Một thư viện nên phân biệt **public configuration API** và **implementation detail**:

```text
audit-api/
  AuditSink.java
  AuditService.java
  EnableAudit.java               <-- annotation public
  AuditLevel.java                <-- lựa chọn public

audit-spring/
  AuditImportSelector.java       <-- internal implementation
  BasicAuditConfig.java
  DetailedAuditConfig.java
  AuditServiceConfig.java

shop-app/
  ShopApplication.java           <-- dùng @EnableAudit
```

Đây là mô hình kiến trúc minh họa; cấu trúc build thực tế phải bảo đảm annotation và selector/config classes có mặt trong runtime dependency. Nếu chia làm nhiều JAR, hãy kiểm tra cycle dependencies: annotation trong module API không thể tham chiếu `AuditImportSelector.class` ở module implementation nếu implementation lại phụ thuộc ngược vào API trong cách chia module đơn giản. Có thể đặt annotation cùng module Spring integration hoặc tách contracts/entrypoint sao cho hướng dependency không tạo vòng.

Với module thật, một cấu trúc thường dễ triển khai hơn:

```text
audit-contracts/
  AuditSink.java
  AuditService.java

audit-spring/
  EnableAudit.java
  AuditLevel.java
  AuditImportSelector.java
  configurations...

shop-app/
  ShopApplication.java
```

`audit-spring` phụ thuộc `audit-contracts`, còn `shop-app` phụ thuộc cả hai hoặc có dependencies bắc cầu phù hợp. `@EnableAudit` công khai trong module `audit-spring`; consumer không cần biết từng class cấu hình nội bộ.

### 11.1. Giữ cấu hình hạ tầng khỏi component scan quá rộng

Nếu các imported configuration classes mang `@Configuration` và nằm ngay dưới một root package được Spring Boot component-scanned, chúng có thể được phát hiện **độc lập với selector**. Ví dụ cả `BasicAuditConfig` và `DetailedAuditConfig` là `@Configuration` nằm dưới package root Boot, rồi `@EnableAudit` lại chỉ chọn một; scanner có thể đã đăng ký cả hai, gây duplicate bean names/types hoặc khiến disabled vẫn có beans.

Giải pháp thiết kế:

- đặt các internal module configurations ngoài application scan root;
- không để chúng được component-scanned trừ khi cố ý;
- sử dụng explicit module import/auto-config có điều kiện;
- bổ sung integration tests từ Spring Boot application context thật để bảo đảm không đăng ký nhầm.

Đừng nhầm “class được selector chọn” với “class chưa bao giờ được đăng ký từ nguồn khác”.

### 11.2. Không lạm dụng cùng một `@Enable` trên nhiều configurations

Nếu một context nhập nhiều configurations cùng mang `@EnableAudit` nhưng yêu cầu **khác level**, selector có thể gặp hai requests không tương thích. Đây là câu hỏi thiết kế API: cần fail fast, quy tắc precedence hay hỗ trợ nhiều audit providers?

Đừng dựa vào thứ tự scan/import để đoán config nào thắng. Với capability chỉ cho phép một provider, đặt một policy rõ, tránh lặp `@Enable...` ở những nơi khác nhau trong cùng composition root.

### 11.3. Spring Boot starter và auto-configuration

Một reusable Spring Boot starter thường khai báo **auto-configuration** theo các convention/metadata của phiên bản Boot đang dùng và dùng điều kiện như classpath, properties và missing beans. Không nhất thiết yêu cầu mọi người tiêu dùng gắn custom `@Enable...`.

Custom `@Enable...` hợp lý khi người dùng muốn **opt-in tường minh**, có attributes thể hiện lựa chọn, hoặc một integration cần kích hoạt chỉ tại một cấu hình. Auto-configuration hợp lý khi muốn behavior plug-and-play nhưng có conditional semantics chặt chẽ. Có thể kết hợp, song phải tránh đăng ký chồng cùng capability.

## 12. Những lỗi thường gặp và cách xử lý

| Triệu chứng | Nguyên nhân khả dĩ | Cách xử lý |
| --- | --- | --- |
| Annotation `@EnableAudit` không có tác dụng | Class dùng annotation chưa được Spring register/import/scan | Kiểm tra configuration entry point thực tế |
| `selectImports` không chạy | Meta-annotation `@Import` thiếu hoặc selector class không hợp lệ | Kiểm tra `@Target`, `@Retention`, imports, classpath |
| Selector trả `"auditService"` | Trả bean name thay vì class name | Trả `AuditServiceConfig.class.getName()` |
| Đọc metadata trả `null` | Sai annotation type hoặc selector không được gọi từ annotation dự kiến | Kiểm tra `EnableAudit.class.getName()` và class importing |
| Cast `level` lỗi | Kiểu enum/attribute không khớp | Dùng đúng `AuditLevel` và metadata accessor |
| Tắt `enabled=false` nhưng context lỗi dependency | Consumer vẫn được đăng ký, producer bị loại | Tắt cả module graph hoặc dùng no-op/optional consumer |
| BASIC và DETAILED cùng thành bean | Configuration khác scan/import cả hai | Rà soát component scan roots và import graph |
| Trùng bean name `auditSink` | Cả hai provider configs được đăng ký | Chỉ đăng ký provider đã chọn, hoặc định danh rõ |
| `NoUniqueBeanDefinitionException` | Có nhiều `AuditSink` implementations cùng type | `@Qualifier`, `@Primary` hoặc module selection rõ |
| Context thiếu bean dù `@EnableAudit` được xử lý | Imported config bị `@Profile`/`@Conditional` loại | Kiểm tra profile, conditions, classpath |
| Property thay đổi sau `refresh` mà tính năng không đổi | Selection là bootstrap decision | Thiết kế runtime flag/facade riêng nếu cần |
| Test gọi `new AppConfig()` rồi mong có bean | Không có Spring container xử lý metadata | Tạo `AnnotationConfigApplicationContext` |
| Dùng `@ImportResource` thay `@Import` trong custom annotation | Nhầm XML resource với Java class imports | Dùng `@Import` cho Java classes/selectors/registrars |
| Annotation dùng String secret | Secrets bị compile vào class metadata | Sử dụng secret provider/environment có kiểm soát |
| Registrar đụng bean name có sẵn | Registry keys trùng hoặc registration chạy nhiều lần | Kiểm tra tồn tại, naming policy và module ownership |
| Custom `@Enable` khiến startup chậm | Selector làm I/O hoặc quét package quá rộng | Giữ selector nhẹ, import có mục tiêu, đo startup |
| Proxy feature không áp dụng khi self-call | Spring AOP self-invocation bypass proxy thông thường | Thiết kế service boundaries phù hợp, không nhầm với import |
| Hai contexts cho kết quả khác nhau | Mỗi context có registry/profile/conditions riêng | Kiểm tra từng context, không giả định singleton toàn JVM |

### 12.1. Quy trình debug theo bằng chứng

1. **Xem entry point:** context được tạo từ class nào? Class đó có `@Enable...` trực tiếp/meta-annotated không?
2. **Theo dõi import graph:** annotation dẫn đến config, selector hay registrar nào? Chúng chọn/đăng ký chính xác những gì?
3. **Kiểm tra metadata:** type của `enabled`, `level` và source annotations có như dự kiến không?
4. **Xem bean candidates:** sau khi context refresh thành công, gọi `getBeanNamesForType(AuditSink.class)` và `getBeanNamesForType(AuditService.class)`.
5. **Đối chiếu disabled mode:** trong thiết kế mục 8, cả hai counts cần bằng zero.
6. **Xem config bổ sung:** component scan, Boot auto-config, profile và conditional declarations có đăng ký provider khác không?
7. **Đọc exception root cause:** thiếu bean, trùng tên hay nhiều ứng viên cùng type là những lỗi khác nhau; không chữa tất cả bằng `@Primary`.
8. **Kiểm thử context nhỏ và context ứng dụng thật:** tránh chỉ unit test selector rồi bỏ qua cách packaging/component scanning vận hành ở production.

## 13. Kiểm thử annotation `@Enable...` ở các tầng

### 13.1. Kiểm thử hợp đồng của selector

Một unit test có thể tạo metadata cho configuration class ở mức test, rồi kiểm tra mảng class names được chọn. Các trường hợp tối thiểu:

| Annotation trên class | Kỳ vọng |
| --- | --- |
| `@EnableAudit` | BASIC config + service config |
| `@EnableAudit(level=DETAILED)` | DETAILED config + service config |
| `@EnableAudit(enabled=false)` | Mảng import rỗng |
| Annotation không có mặt nhưng selector vẫn được gọi trực tiếp | Báo lỗi metadata rõ ràng |

Nếu tách logic quyết định thành một hàm thuần như `selectConfigs(boolean enabled, AuditLevel level)`, unit test có thể kiểm tra nhanh mà không phải dựng container. Nhưng bài test ấy chưa xác thực cơ chế `@Import` thật.

### 13.2. Integration test cần xác nhận registry và DI

Ví dụ một test khi dùng types của mục 8:

```java
try (var context =
        new AnnotationConfigApplicationContext(
                EnableAuditDemo.DetailedApp.class)) {

    var sinkNames = context.getBeanNamesForType(
            EnableAuditDemo.AuditSink.class);
    var service = context.getBean(
            EnableAuditDemo.AuditService.class);

    if (sinkNames.length != 1) {
        throw new AssertionError("Expected one AuditSink");
    }
    if (!"detailed:checkout|trace".equals(
            service.report("checkout"))) {
        throw new AssertionError("Wrong audit implementation");
    }
}
```

Đây là một **trích đoạn test** dùng các public nested types từ demo, không phải source file độc lập. Test như thế xác minh **annotation → import → bean definition → dependency injection → behavior**, hơn là chỉ kiểm tra selector trả tên class đúng.

### 13.3. Tại sao cần test disabled?

Một chức năng chỉ chạy đúng khi enabled chưa đủ an toàn. Tắt tính năng có thể để lại bean consumers chưa được thỏa dependency hoặc các components bị scan tự động. Với `OFF`, hãy kiểm tra:

- Context có thể refresh thành công.
- Không có `AuditSink`.
- Không có `AuditService` theo contract đã công bố.
- Không còn scheduled task/side effect thuộc capability nếu implementation ngoài ví dụ có thêm infrastructure.
- Không có một auto-configuration khác bật audit lại ngoài ý muốn.

### 13.4. Test nhiều context có một ý nghĩa riêng

Các tests tạo contexts BASIC, DETAILED và OFF tách biệt giúp tránh dùng lẫn registry và giữ output ổn định. Singleton Spring không phải singleton toàn JVM; hai contexts có thể có hai instances/tập beans khác nhau mà không mâu thuẫn.

## 14. Bài tập thực hành và đáp án

### Bài 1 — Khi nào cần selector?

Một module chỉ có duy nhất `MetricsConfig`, không có attributes và không đổi theo environment. Có nên viết `ImportSelector` không?

**Đáp án:** Không bắt buộc. Dùng `@Import(MetricsConfig.class)` trực tiếp trong custom `@EnableMetrics` là cách đơn giản, rõ nghĩa hơn.

### Bài 2 — Điều gì khiến annotation thật sự có tác dụng?

Một class `AppConfig` mang `@EnableAudit` nhưng chưa bao giờ được register/scan/import vào bất kỳ `ApplicationContext` nào. Có bean audit không?

**Đáp án:** Không do annotation ấy. Metadata không tự kích hoạt ngoài container; Spring phải xử lý configuration class.

### Bài 3 — Hai class được import khi bật

Selector trả `BasicAuditConfig.class.getName()` nhưng quên `AuditServiceConfig.class.getName()`. `AuditService` có được tự đăng ký không?

**Đáp án:** Không qua selector đó. Basic config chỉ tạo `AuditSink`, còn service cần một nguồn registration riêng. Thiết kế module graph phải đủ.

### Bài 4 — OFF mode tạo lỗi

Selector trả mảng rỗng khi disabled, nhưng `AppConfig` còn khai báo `@Bean CheckoutService checkoutService(AuditService service)` không optional. Điều gì xảy ra?

**Đáp án:** Context có thể fail do thiếu bean `AuditService`. Cần disable cả consumer, dùng optional dependency hoặc cung cấp no-op implementation theo hợp đồng.

### Bài 5 — Chọn dựa trên enum

`@EnableAudit(level=DETAILED)` ở demo có bao nhiêu `AuditSink`?

**Đáp án:** Một, nếu không có registrations bổ sung. Selector chỉ chọn `DetailedAuditConfig` và `AuditServiceConfig`.

### Bài 6 — Trùng bean name

Cả `BasicAuditConfig` và `DetailedAuditConfig` cùng tồn tại trong một context do một scanner khác, đều khai báo `@Bean auditSink()`. Có nguy cơ gì?

**Đáp án:** Xung đột/ghi đè bean name hoặc nhầm provider. Hãy sửa phạm vi đăng ký, tránh scan các internal configs ngoài selector.

### Bài 7 — `@AliasFor` có bắt buộc cho `level`?

Custom `@EnableAudit` có riêng `AuditLevel level()`, selector đọc metadata. Có phải alias `level` sang `@Import.value` không?

**Đáp án:** Không. `@Import.value` nhận class array, không nhận AuditLevel. Attribute `level` là input riêng cho selector.

### Bài 8 — Thay property khi ứng dụng đã chạy

Thay `audit.level=BASIC` thành `DETAILED` sau `ApplicationContext.refresh()` có tự thay imported config không?

**Đáp án:** Không. Import decisions thuộc giai đoạn bootstrap; runtime switching cần mechanism khác.

### Bài 9 — Dùng `@EnableCaching` thiếu CacheManager

Một ứng dụng khai báo `@EnableCaching` và `@Cacheable` trên managed bean, nhưng chưa cấu hình cache provider/manager phù hợp. Có nên giả định caching hoàn toàn hoạt động?

**Đáp án:** Không. Annotation bật infrastructure, nhưng các phụ thuộc của infrastructure vẫn phải được thỏa mãn. Cần kiểm tra cache configuration và calls thực tế.

### Bài 10 — Sự khác biệt selector và registrar

Library muốn chọn một trong hai `@Configuration` classes. Library khác muốn đăng ký nhiều bean definitions theo metadata annotation. Chọn công cụ nào?

**Đáp án:** `ImportSelector` cho lựa chọn class configurations; `ImportBeanDefinitionRegistrar` cho đăng ký definitions cấp thấp có tính động.

### Bài 11 — Quan hệ phụ thuộc giữa các factory methods

`AuditServiceConfig` muốn dùng `AuditSink` được config khác tạo. Có nên new một `BasicAuditSink` trực tiếp trong service method?

**Đáp án:** Không nếu muốn module selector quyết định implementation. Viết `@Bean AuditService auditService(AuditSink sink)`; BeanFactory cung cấp managed dependency.

### Bài 12 — Scope của enable annotation

Hai `AnnotationConfigApplicationContext` độc lập đều dùng `@EnableAudit`. Có bắt buộc cùng dùng một singleton `AuditSink` toàn JVM không?

**Đáp án:** Không. Spring singleton scope là trong một container/BeanFactory; các contexts độc lập có bean registries riêng.

### Bài 13 — Bật transaction bằng custom @Enable?

Một người muốn tạo `@EnablePayments`, bên trong chỉ import `PaymentServiceConfig` và mong mọi method tự động có transaction. Điều đó đúng không?

**Đáp án:** Không. `@EnablePayments` trong thiết kế ấy chỉ nhập bean definitions do config công bố. Transaction management cần hạ tầng riêng, transaction manager và interception/cơ chế phù hợp; không xuất hiện chỉ từ việc đặt tên annotation là Enable.

### Bài 14 — API công khai của module

Thư viện có các lớp `DetailedAuditConfig` và `AuditImportSelector` được đổi package giữa các phiên bản, nhưng vẫn giữ `EnableAudit` và `AuditLevel` ổn định. Ứng dụng có cần biết package mới của hai lớp nội bộ không?

**Đáp án:** Thường không nếu entry point và contract public giữ nguyên, và jar/module thư viện cung cấp đúng classes. Đây là một lợi ích chính của annotation façade cho cấu hình.

## 15. Checklist trước khi phát hành custom `@Enable...`

- Tính năng có cần annotation bật tường minh, hay `@Import`/`@Conditional` đã đủ?
- Annotation có `@Target(TYPE)`, `@Retention(RUNTIME)`, `@Documented` và đúng `@Import` chưa?
- Attributes có ý nghĩa nghiệp vụ, tên rõ, defaults hợp lý và types compiler-checkable chưa?
- Selector/registrar chỉ làm công việc bootstrap, không tự tạo business objects bên ngoài BeanFactory chứ?
- Khi **enabled**, mọi producer/consumer/configuration cần thiết có được đăng ký nhất quán không?
- Khi **disabled**, các consumer bắt buộc có bị bỏ lại với dependency thiếu không?
- Có khả năng config bị scan hoặc auto-config đăng ký thêm từ đường khác không?
- Selector có trả **fully qualified class names** và xử lý classpath/profiles/conditions không?
- Có bean name conflicts hoặc nhiều candidates cùng interface không?
- Các tham số `@Bean`/constructors đã thể hiện dependency rõ thay vì gọi trực tiếp config methods chưa?
- Có hiểu đúng runtime toggle khác bootstrap-time enable không?
- Public API có tránh lộ secrets hoặc internal class names không?
- Đã test annotation ở cả BASIC, DETAILED, OFF và kiểm chứng số lượng bean cùng behavior trong Spring context thật chưa?

## 16. Tổng kết

Mẫu **`@Enable...`** biến một tập cấu hình và extension points thành API khai báo gọn: người dùng chỉ gắn một annotation trên configuration class, còn thư viện dùng **`@Import`** để đăng ký cấu hình cố định, chọn bằng **`ImportSelector`**, hoặc đăng ký bean definitions qua **`ImportBeanDefinitionRegistrar`** khi cần.

Thiết kế tốt không chỉ là làm cho annotation “có tác dụng” khi bật; nó phải làm cho **enabled/disabled semantics, dependency graph, bean ownership và classpath boundaries** nhất quán. Với use case đơn giản, nhập configuration cố định; với choices theo attributes, dùng selector; với registration hạ tầng rất động, cân nhắc registrar. Tránh tạo infrastructure phức tạp chỉ để trông giống một annotation có sẵn của Spring.

**Nguyên tắc cuối:** `@Enable...` là quyết định ở lúc **configuration processing**, không mặc nhiên là công tắc runtime. Tất cả object được chọn vẫn cần bean definitions hợp lệ, đúng scope/qualifier và được BeanFactory quản lý.

### Tài liệu Spring chính thức

- [Spring Framework 6.2 — `@Import` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/annotation/Import.html).
- [Spring Framework 6.2 — `ImportSelector` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/annotation/ImportSelector.html).
- [Spring Framework 6.2 — `ImportBeanDefinitionRegistrar` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/annotation/ImportBeanDefinitionRegistrar.html).
- [Spring Framework — Composing Java-based Configurations](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/composing-configuration-classes.html).
- [Spring Framework 6.2 — `@EnableCaching` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/cache/annotation/EnableCaching.html).
- [Spring Framework 6.2 — `@EnableAsync` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/EnableAsync.html).
- [Spring Framework 6.2 — `@EnableScheduling` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/EnableScheduling.html).
- [Spring Framework 6.2 — `@EnableTransactionManagement` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/transaction/annotation/EnableTransactionManagement.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="configuration-composition-009">Tránh phụ thuộc vòng giữa các lớp cấu hình</a>

<details>
<summary>Xem chi tiết</summary>

# Tránh phụ thuộc vòng giữa các lớp cấu hình trong Spring Core Container

## Mục tiêu bài học

Khi ứng dụng Spring mở rộng, việc chia Java configuration thành nhiều class `@Configuration` giúp tổ chức các bean theo module: cơ sở dữ liệu, repositories, services, integrations và entry points. Tuy nhiên, nếu các class cấu hình **import lẫn nhau**, **constructor-inject lẫn nhau** hoặc **truy cập quá sớm những bean do chính chúng định nghĩa**, bạn có thể tạo ra một *circular dependency* (phụ thuộc vòng).

Một lỗi thường gặp là:

```text
ServiceConfig  --->  RepositoryConfig
     ^                    |
     |                    |
     +--------------------+
```

Mũi tên đó có thể là `@Import`, constructor injection giữa hai configuration class, hoặc một dependency giữa các đối tượng được khai báo bằng `@Bean`. **Ba trường hợp này không đồng nghĩa**, và cách chẩn đoán/sửa đúng phải dựa trên loại cạnh nào đang tạo vòng.

Bài này tập trung vào chủ đề **“Tránh phụ thuộc vòng giữa các lớp cấu hình”**, thuộc phần *Ghép cấu hình* của Spring Framework Core Container. Sau bài học, bạn sẽ:

1. Phân biệt **vòng import configuration classes**, **vòng tạo configuration bean** và **vòng dependency giữa application beans**.
2. Dùng `@Import` theo cấu trúc một chiều thay vì để các module config biết và nhập lẫn nhau.
3. Dùng **tham số phương thức `@Bean`** để biểu diễn dependency giữa bean definitions ở các configuration classes khác nhau.
4. Hiểu vì sao constructor injection vào chính `@Configuration` đôi lúc gây **early initialization/self-reference cycle**.
5. Tránh gọi `@Bean` methods trong `@PostConstruct` của class cấu hình.
6. Hiểu sự khác nhau giữa full `@Configuration` và `@Configuration(proxyBeanMethods=false)`; biết khi nào direct method call tạo unmanaged object.
7. Đọc nguyên nhân thực của `BeanCurrentlyInCreationException`, `UnsatisfiedDependencyException` và vòng `@Import`.
8. Đánh giá giới hạn của `@Lazy`, `ObjectProvider`, `@DependsOn` và tùy chọn cho phép circular references.
9. Có mẫu thiết kế hoàn chỉnh Java 17 + Spring Framework dùng root composition và wiring theo kiểu, không có vòng.
10. Viết integration test để phát hiện vòng sớm và tránh regression.

**Nguyên tắc xuyên suốt:** Class `@Configuration` nên **khai báo cách tạo bean**, không nên trở thành service cần phụ thuộc trực tiếp vào configuration class khác. Hãy để **bean nghiệp vụ phụ thuộc vào các abstraction thích hợp**, còn một **root configuration** tổng hợp module theo một chiều.

## 1. Ba loại vòng thường bị đánh đồng

Không phải mọi vòng trong Spring đều thuộc cùng một tầng. Trước khi sửa code, hãy vẽ được đồ thị và xác định **node** là cái gì.

### 1.1. Vòng import: class cấu hình A import B, B import A

```java
// PHẢN VÍ DỤ: Hai class cấu hình nhập lẫn nhau.
@Configuration
@Import(ConfigB.class)
class ConfigA {
}

@Configuration
@Import(ConfigA.class)
class ConfigB {
}
```

Đồ thị:

```text
ConfigA --@Import--> ConfigB
ConfigB --@Import--> ConfigA
```

`@Import` nói với Spring “hãy xử lý thêm class cấu hình này”, **không phải** “hãy tạo một đối tượng làm dependency cho class kia”. Khi import graph có vòng, Spring configuration parser có thể phát hiện và từ chối, ví dụ thông báo liên quan `A circular @Import has been detected` hoặc lỗi phân tích configuration.

Cách sửa thông thường là **đưa quyền phối hợp lên một root**:

```java
@Configuration(proxyBeanMethods = false)
@Import({ConfigA.class, ConfigB.class})
class RootConfig {
}

// ConfigA và ConfigB không còn @Import lẫn nhau.
```

Về mặt tổ chức, root biết những module cần lắp vào một ứng dụng; những module không nhất thiết phải biết cấu hình của nhau.

### 1.2. Vòng constructor giữa chính các @Configuration beans

```java
// PHẢN VÍ DỤ: Cả hai class cấu hình cần instance của nhau.
@Configuration
class BillingConfig {
    BillingConfig(ShippingConfig shippingConfig) {
    }
}

@Configuration
class ShippingConfig {
    ShippingConfig(BillingConfig billingConfig) {
    }
}
```

Đồ thị:

```text
BillingConfig instance --> ShippingConfig instance
ShippingConfig instance --> BillingConfig instance
```

Spring xử lý `@Configuration` như những beans trong container. Nếu phải tạo `BillingConfig` trước mới tạo được `ShippingConfig`, nhưng cũng phải tạo `ShippingConfig` trước mới tạo được `BillingConfig`, constructor injection không thể hoàn thành.

Điểm quan trọng: vòng này xảy ra **giữa bản thân configuration instances**, chứ chưa chắc giữa `InvoiceService` và `ShippingService`. Cách tốt nhất thường là **không inject configuration class vào configuration class**; thay bằng dependency theo kiểu của **bean cần dùng** ngay tại `@Bean` method.

### 1.3. Vòng constructor giữa beans do @Bean tạo

```java
// PHẢN VÍ DỤ: Hai business types phải có nhau ngay trong constructor.
@Configuration(proxyBeanMethods = false)
class BadBusinessConfig {

    @Bean
    ServiceA serviceA(ServiceB b) {
        return new ServiceA(b);
    }

    @Bean
    ServiceB serviceB(ServiceA a) {
        return new ServiceB(a);
    }
}
```

Ở đây `ServiceA` và `ServiceB` là kiểu minh họa có constructors nhận nhau. Đồ thị thực:

```text
bean "serviceA" --> bean "serviceB"
bean "serviceB" --> bean "serviceA"
```

Khi Spring tạo bean A cần B, tạo B lại cần A đang được tạo. Vòng constructor bất khả giải thường dẫn đến `BeanCurrentlyInCreationException`, có thể nằm trong exception chain gồm `UnsatisfiedDependencyException` hoặc `BeanCreationException`.

**Khác biệt thiết yếu:** Việc chuyển hai `@Bean` methods sang hai `@Configuration` classes **không tự làm vòng bean biến mất**. Bạn cần sửa **hướng dependency giữa application beans**, không chỉ sửa vị trí khai báo chúng.

## 2. Vì sao nhiều class cấu hình vẫn có thể phụ thuộc bean của nhau?

Spring **không yêu cầu** mỗi configuration class chỉ tạo các bean mà nó hoàn toàn tự sở hữu. Một bean ở module service hoàn toàn có thể phụ thuộc bean repository do module khác khai báo.

Ví dụ:

```java
@Configuration(proxyBeanMethods = false)
class RepositoryConfig {

    @Bean
    AccountRepository accountRepository() {
        return new InMemoryAccountRepository();
    }
}
```

```java
@Configuration(proxyBeanMethods = false)
class ServiceConfig {

    @Bean
    AccountService accountService(AccountRepository accountRepository) {
        return new AccountService(accountRepository);
    }
}
```

```java
@Configuration(proxyBeanMethods = false)
@Import({RepositoryConfig.class, ServiceConfig.class})
class ApplicationConfig {
}
```

Đây là **mẫu minh họa**, cần các kiểu `AccountRepository`, `InMemoryAccountRepository` và `AccountService` có constructor phù hợp.

Đồ thị import:

```text
             ApplicationConfig
                /       \
           @Import      @Import
              /           \
RepositoryConfig         ServiceConfig
```

Đồ thị dependency **giữa beans**:

```text
accountService --> accountRepository
```

Không có vòng. `ServiceConfig` **không cần import `RepositoryConfig`** chỉ vì method `accountService(AccountRepository)` yêu cầu bean repository. Khi root đã đưa cả hai config vào cùng context, BeanFactory nhìn thấy definitions và resolve tham số theo kiểu.

Đây là điểm quan trọng trong thiết kế module: **class cấu hình có thể độc lập về source-level reference, trong khi các bean của chúng vẫn được wiring qua IoC container**.

### Vai trò của @Import

`@Import` thuộc:

```java
import org.springframework.context.annotation.Import;
```

Nó giúp root configuration gom các cấu hình hoặc components phù hợp để Spring xử lý. Các tham số là **class references**, không phải bean instance. `@Import` không tự inject một configuration instance vào một configuration khác.

### Vai trò của tham số @Bean

```java
@Bean
AccountService accountService(AccountRepository repository) {
    return new AccountService(repository);
}
```

Tham số `AccountRepository` là **dependency request** đối với BeanFactory, giúp Spring phân giải bean theo type, qualifier, primary, generic và scope thích hợp. Không có lý do phải gọi `new RepositoryConfig().accountRepository()` hay giữ một field `RepositoryConfig` trong `ServiceConfig` để lấy bean.

## 3. Thiết kế một chiều: root composition và các module tự chủ

Đối với ứng dụng có cấu trúc theo tầng, một kiến trúc dễ bảo trì là:

```text
                     RootConfig
                   /     |      \
                  v      v       v
              Infra    Repo    Service
              Config   Config  Config

Bean dependencies:
CatalogService  ---> CatalogRepository
       |
       +-----------> Clock
       |
       v
CatalogFacade
```

Các **configuration classes** được import từ root. Các **application beans** được nối qua injection. Không cần có cạnh `ServiceConfig -> RepositoryConfig` nếu chỉ muốn service dùng một repository bean.

### Đặt ranh giới module

- `InfrastructureConfig` chịu trách nhiệm đăng ký clock, data source, HTTP client, codec hoặc hạ tầng chung.
- `RepositoryConfig` chịu trách nhiệm repository implementations.
- `ServiceConfig` chịu trách nhiệm service beans.
- `RootConfig` quyết định ứng dụng cần những module nào.
- Không module nào cần giữ instance configuration class của module khác.

Tổ chức này làm rõ **dependency direction**. Có thể dùng cấu trúc package theo feature thay vì theo layer; nguyên tắc vẫn là chọn root composition rõ và không tạo vòng.

### Nếu một module thực sự cần dùng module khác?

Phân biệt hai câu hỏi:

1. “Ứng dụng có cần nhập cả hai cấu hình không?” — root `@Import` xử lý.
2. “Bean A có cần dùng bean B không?” — constructor hoặc factory method parameter injection xử lý.

Đừng trả lời cả hai bằng “cấu hình A import cấu hình B và nhận luôn BConfig trong constructor”. Điều đó tăng coupling và dễ dẫn đến vòng khi module B cần import A sau này.

## 4. Anti-pattern: inject class cấu hình khác để gọi factory method

Ví dụ khó bảo trì:

```java
@Configuration
class RepositoryConfig {

    @Bean
    CatalogRepository catalogRepository() {
        return new JdbcCatalogRepository();
    }
}

@Configuration
class ServiceConfig {

    private final RepositoryConfig repositoryConfig;

    ServiceConfig(RepositoryConfig repositoryConfig) {
        this.repositoryConfig = repositoryConfig;
    }

    @Bean
    CatalogService catalogService() {
        return new CatalogService(
                repositoryConfig.catalogRepository());
    }
}
```

Đây là **đoạn phản ví dụ** với những class ứng dụng giả định. Có thể có tình huống lời gọi được full configuration proxy intercept, nhưng nó khiến `ServiceConfig` phụ thuộc vào **implementation/configuration class** của module repository thay vì dependency contract `CatalogRepository`.

Rủi ro:

- Khi refactor cách đăng ký repository, service config phải đổi theo.
- Khi repo config có dependency tới service module, vòng giữa config instances dễ xuất hiện.
- Dễ nhầm giữa gọi method Java và lấy Spring-managed singleton, đặc biệt nếu chuyển sang `proxyBeanMethods=false`.
- Unit test của factory method không cần thiết phải dựng configuration instance khác.
- Khó chuyển bean sang XML, registrar hoặc component scan mà không sửa code wiring.

### Sửa bằng tham số factory method

```java
@Configuration(proxyBeanMethods = false)
class ServiceConfig {

    @Bean
    CatalogService catalogService(CatalogRepository repository) {
        return new CatalogService(repository);
    }
}
```

Trong root:

```java
@Configuration(proxyBeanMethods = false)
@Import({RepositoryConfig.class, ServiceConfig.class})
class RootConfig {
}
```

Như vậy `ServiceConfig` không cần biết `RepositoryConfig` là class nào. BeanFactory đáp ứng dependency theo contract.

## 5. Anti-pattern: configuration class inject bean do chính nó khai báo

Vòng có thể tinh vi hơn khi class cấu hình tự yêu cầu **kết quả từ instance method của chính nó**.

```java
// PHẢN VÍ DỤ: StorageConfig cần Storage để được tạo,
// nhưng Storage lại được tạo từ instance method trên StorageConfig.
@Configuration
class StorageConfig {

    private final Storage storage;

    StorageConfig(Storage storage) {
        this.storage = storage;
    }

    @Bean
    Storage storage() {
        return new Storage();
    }
}
```

Đồ thị:

```text
configuration bean "storageConfig"
           |
           | constructor requires Storage
           v
bean "storage"
           |
           | instance @Bean factory requires storageConfig instance
           v
configuration bean "storageConfig"
```

Spring phải khởi tạo configuration instance trước khi gọi non-static factory method `storage()` trên instance đó, trong khi constructor config lại đòi bean do method ấy sản xuất. Đây là một **self-reference cycle** ở giai đoạn tạo bean.

### Cách sửa 1: không đưa Storage vào constructor configuration

```java
@Configuration(proxyBeanMethods = false)
class StorageConfig {

    @Bean
    Storage storage() {
        return new Storage();
    }

    @Bean
    StorageConsumer storageConsumer(Storage storage) {
        return new StorageConsumer(storage);
    }
}
```

Trong ví dụ, `StorageConsumer` có constructor nhận `Storage`. Cấu hình không giữ `Storage` trong field; Spring inject nó trực tiếp vào factory method cần dùng.

### Cách sửa 2: dùng một cấu hình hạ tầng độc lập

```java
@Configuration(proxyBeanMethods = false)
class InfrastructureConfig {

    @Bean
    Storage storage() {
        return new Storage();
    }
}

@Configuration(proxyBeanMethods = false)
class ConsumerConfig {

    @Bean
    StorageConsumer storageConsumer(Storage storage) {
        return new StorageConsumer(storage);
    }
}
```

Một root import cả hai. Không config nào phải inject bean do chính instance factory method của nó tạo.

### Cách sửa 3: static @Bean khi thuộc loại hạ tầng phù hợp

Một số bean post-processors được khai báo bằng `static @Bean`, cho phép BeanFactory tạo chúng mà **không phải tạo instance configuration class quá sớm**. Nhưng đây là trường hợp hạ tầng có yêu cầu lifecycle riêng, không phải giải pháp chung để biến mọi bean thành static.

Ví dụ:

```java
// Đoạn mô tả, không phải lời khuyên áp dụng cho mọi @Bean.
@Configuration(proxyBeanMethods = false)
class InfrastructureConfig {
    @Bean
    static SomeBeanFactoryPostProcessor somePostProcessor() {
        return new SomeBeanFactoryPostProcessor();
    }
}
```

`SomeBeanFactoryPostProcessor` cần là một implementation phù hợp. Điểm quan trọng: dùng static khi bean thực sự thuộc post-processor bootstrap, và hiểu sự khác biệt với instance factory methods.

## 6. Đừng gọi bean của cấu hình trong @PostConstruct trên chính @Configuration

Đây là cảnh báo rõ trong tài liệu Spring về composing Java configurations.

```java
// PHẢN VÍ DỤ: Không lấy bean do chính config định nghĩa
// trong @PostConstruct của class config này.
@Configuration
class RiskyConfig {

    @Bean
    DataClient dataClient() {
        return new DataClient();
    }

    @PostConstruct
    void init() {
        dataClient().connect();
    }
}
```

Đoạn này dùng `@PostConstruct` của Jakarta Annotations và giả định `DataClient` có method `connect()`. Nó chỉ nhằm mô tả rủi ro, không phải một ứng dụng an toàn.

Vì `@Configuration` cũng là một bean và instance method `@Bean` cần instance config đã sẵn sàng, việc cố lấy một locally declared bean **ngay khi config instance còn ở giai đoạn init** có thể tạo self-reference/circular creation. Khi circular references bị cấm, bạn có thể nhận `BeanCurrentlyInCreationException`.

Ngoài ra:

- `@PostConstruct` chạy trong **lifecycle của bean cấu hình**, không có nghĩa toàn bộ application đã khởi động xong.
- Nếu gọi một factory method trực tiếp ở lite mode, Java có thể tạo object riêng **không được BeanFactory quản lý**.
- Mở network connection hoặc chạy nghiệp vụ trong initialization callback còn có thể làm startup chậm, gây deadlock hoặc leak tài nguyên.
- Việc thêm `@Lazy` vào một bean không đảm bảo self-invocation trong init sẽ an toàn.

### Cách sửa: bean-local initialization của bean được quản lý

Nếu `DataClient` có method `connect()` phù hợp cho lifecycle của **chính nó**:

```java
@Configuration(proxyBeanMethods = false)
class ClientConfig {

    @Bean(initMethod = "connect", destroyMethod = "close")
    DataClient dataClient() {
        return new DataClient();
    }
}
```

Spring gọi `connect` trên **managed DataClient bean**, rồi `close` theo lifecycle singleton khi context đóng. Không cần gọi `dataClient()` từ `@PostConstruct` của `ClientConfig`.

Nếu thao tác là bootstrap nghiệp vụ phối hợp nhiều beans, dùng coordinator/startup lifecycle riêng phù hợp; đừng đẩy nó vào config initialization.

## 7. Full và lite @Configuration không làm biến mất vòng thật

Với full configuration:

```java
@Configuration
class FullConfig {
    @Bean
    X x() {
        return new X(y());
    }

    @Bean
    Y y() {
        return new Y();
    }
}
```

Khi lời gọi `y()` diễn ra trên enhanced configuration instance do Spring quản lý, Spring có thể intercept để lấy bean `y` theo scope. Điều này giúp dùng đúng bean identity trong một đồ thị dependency **không có vòng**.

Với lite configuration:

```java
@Configuration(proxyBeanMethods = false)
class LiteConfig {
    @Bean
    X x() {
        return new X(y());
    }

    @Bean
    Y y() {
        return new Y();
    }
}
```

Lời gọi `y()` trong `x()` chỉ là Java call thông thường; nó có thể tạo object `Y` riêng, không phải managed bean `y`. Đây là **lỗi identity/lifecycle**, không nhất thiết báo circular dependency; do vậy lite mode **không phải “thuốc sửa vòng”** mà còn có thể che rủi ro khác.

### Mẫu chung cho cả full và lite

```java
@Configuration(proxyBeanMethods = false)
class SafeConfig {
    @Bean
    X x(Y y) {
        return new X(y);
    }

    @Bean
    Y y() {
        return new Y();
    }
}
```

Spring resolve tham số factory method nên identity đúng. Nếu `Y` lại phụ thuộc vào `X` qua constructor, bạn vẫn có **vòng bean thật** và phải refactor dependency direction.

**Nhớ:** Đổi `proxyBeanMethods=true` sang `false` không xóa vòng import hoặc vòng constructor. Nó chỉ thay đổi cách những lời gọi Java tới bean methods được xử lý. Luôn kiểm tra **đồ thị dependency giữa các beans**, không chỉ cờ configuration enhancement.

## 8. Ví dụ Java 17 + Spring Framework: ghép cấu hình không có vòng

Ví dụ runnable dưới đây minh họa một ứng dụng nhỏ có:

- `InfrastructureConfig` tạo `Clock` cố định để output deterministic.
- `RepositoryConfig` tạo `CatalogRepository`.
- `ServiceConfig` tạo `CatalogService` bằng **tham số `@Bean`** nhận hai dependencies.
- `RootConfig` nhập ba cấu hình đó bằng **một chiều `@Import`** và tạo `CatalogFacade`.
- Không config nào inject một configuration class khác.
- Bean dependencies có hướng một chiều và Spring quản lý singleton identity.

Dự án chỉ cần Java 17+, Maven và `spring-context`, không yêu cầu Spring Boot, web server, database hay mạng bên ngoài.

### 8.1. Cấu trúc dự án

```text
config-cycle-free/
├── pom.xml
└── src/main/java/
    └── demo/
        └── CycleFreeConfigDemo.java
```

### 8.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="
             http://maven.apache.org/POM/4.0.0
             https://maven.apache.org/xsd/maven-4.0.0.xsd">

    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>config-cycle-free</artifactId>
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

### 8.3. Mã Java đầy đủ

Tạo `src/main/java/demo/CycleFreeConfigDemo.java`:

```java
package demo;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

public class CycleFreeConfigDemo {

    public interface CatalogRepository {
        String labelFor(String sku);
    }

    public static final class InMemoryCatalogRepository
            implements CatalogRepository {

        @Override
        public String labelFor(String sku) {
            return switch (sku) {
                case "BK-7" -> "Java book";
                case "MUG-2" -> "Coffee mug";
                default -> "Unknown";
            };
        }
    }

    public static final class CatalogService {
        private final CatalogRepository repository;
        private final Clock clock;

        public CatalogService(CatalogRepository repository, Clock clock) {
            this.repository = repository;
            this.clock = clock;
        }

        public String describe(String sku) {
            return repository.labelFor(sku) + "@" +
                    LocalDate.now(clock);
        }

        public CatalogRepository repository() {
            return repository;
        }
    }

    public static final class CatalogFacade {
        private final CatalogService service;

        public CatalogFacade(CatalogService service) {
            this.service = service;
        }

        public String show(String sku) {
            return "facade:" + service.describe(sku);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class InfrastructureConfig {

        @Bean
        public Clock fixedClock() {
            return Clock.fixed(
                    Instant.parse("2024-01-01T00:00:00Z"),
                    ZoneOffset.UTC);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class RepositoryConfig {

        @Bean
        public CatalogRepository catalogRepository() {
            return new InMemoryCatalogRepository();
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ServiceConfig {

        @Bean
        public CatalogService catalogService(
                CatalogRepository repository, Clock clock) {
            return new CatalogService(repository, clock);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
        InfrastructureConfig.class,
        RepositoryConfig.class,
        ServiceConfig.class
    })
    public static class RootConfig {

        @Bean
        public CatalogFacade catalogFacade(CatalogService service) {
            return new CatalogFacade(service);
        }
    }

    public static void main(String[] args) {
        try (var context =
                     new AnnotationConfigApplicationContext(RootConfig.class)) {

            CatalogRepository repository =
                    context.getBean(CatalogRepository.class);
            CatalogService service =
                    context.getBean(CatalogService.class);
            CatalogFacade facade =
                    context.getBean(CatalogFacade.class);

            System.out.println("repo=" + repository.labelFor("BK-7"));
            System.out.println("service=" + service.describe("BK-7"));
            System.out.println("facade=" + facade.show("MUG-2"));
            System.out.println("sameRepository=" +
                    (service.repository() == repository));

            String[] repositoryNames =
                    context.getBeanNamesForType(CatalogRepository.class);
            Arrays.sort(repositoryNames);
            System.out.println("repositoryBeans=" +
                    Arrays.toString(repositoryNames));
        }
    }
}
```

### 8.4. Lệnh chạy

Chạy từ thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.CycleFreeConfigDemo
```

Các dòng **do chương trình in ra**, không tính log Maven hoặc các logging bindings trong môi trường:

```text
repo=Java book
service=Java book@2024-01-01
facade=facade:Coffee mug@2024-01-01
sameRepository=true
repositoryBeans=[catalogRepository]
```

### 8.5. Vì sao output chính xác?

**`repo=Java book`:** Bean `catalogRepository` do `RepositoryConfig` khai báo, tạo `InMemoryCatalogRepository`. Method `labelFor("BK-7")` trả chuỗi cố định `Java book`.

**`service=Java book@2024-01-01`:** `CatalogService` được Spring tạo qua factory method `catalogService(CatalogRepository, Clock)`. Nó nhận repository singleton và `Clock.fixed(...)` từ `InfrastructureConfig`. `LocalDate.now(clock)` là ngày `2024-01-01`, không phụ thuộc ngày/giờ máy chạy.

**`facade=facade:Coffee mug@2024-01-01`:** Root tạo `CatalogFacade` bằng tham số `CatalogService` do Spring inject. Facade gọi service với `MUG-2`, repository trả `Coffee mug`, facade thêm tiền tố `facade:`.

**`sameRepository=true`:** `CatalogService` nhận **managed repository singleton** do Spring resolve theo type. `getBean(CatalogRepository.class)` trả cùng reference trong cùng context.

**`repositoryBeans=[catalogRepository]`:** Chỉ có một definition kiểu `CatalogRepository`, tên mặc định của `@Bean` factory method là `catalogRepository`. Mọi module cùng dùng một BeanFactory, không có repository phụ được tạo bằng `new RepositoryConfig().catalogRepository()`.

### 8.6. Đồ thị trong ví dụ

```text
RootConfig
  |--@Import--> InfrastructureConfig  --@Bean--> Clock
  |--@Import--> RepositoryConfig      --@Bean--> CatalogRepository
  |--@Import--> ServiceConfig         --@Bean--> CatalogService
  |
  +-------------- @Bean ---------------------> CatalogFacade

Bean dependencies (cạnh ngược chiều nhu cầu):
CatalogFacade ---> CatalogService ---> CatalogRepository
                         |
                         +-----------> Clock
```

Không có đường nào từ repository về service hoặc từ infrastructure về facade. Import graph và bean dependency graph đều **không có vòng**.

### 8.7. Tại sao RootConfig chỉ import, không instantiate config?

`@Import(RepositoryConfig.class)` nói với Spring xử lý class cấu hình đó. Việc viết:

```java
// PHẢN VÍ DỤ: không tạo configuration instances thủ công.
RepositoryConfig repoConfig = new RepositoryConfig();
CatalogRepository repo = repoConfig.catalogRepository();
```

sẽ tự tạo object repository bên ngoài managed bean definition, không có lý do để BeanFactory quản lý nó. Trong lite configuration, direct call còn có thể tạo instance khác singleton. Cách đúng là để `@Import` đưa bean definitions vào container rồi dùng `@Bean` parameter injection.

### 8.8. Nếu thêm module khác?

Giả sử có `NotificationConfig` đăng ký một `NotificationService` phụ thuộc `CatalogService`. Bạn có thể:

1. Tạo `NotificationConfig` với `@Bean NotificationService notificationService(CatalogService service)`.
2. Thêm `NotificationConfig.class` vào mảng import của **RootConfig**.
3. Không để `ServiceConfig` import `NotificationConfig` chỉ vì một service khác dùng catalogue.
4. Giữ dependency nghiệp vụ một chiều; nếu notification lại buộc catalog service phụ thuộc ngược notification, đó là dấu hiệu cần trích abstraction hoặc event.

Root có thể biết toàn bộ các module được ghép để chạy ứng dụng, nhưng các module không cần nắm toàn bộ bản đồ.

## 9. Đọc vòng trong stack trace và bean graph

Khi context không khởi tạo, lỗi cấp cao có thể là:

```text
BeanCreationException
  -> UnsatisfiedDependencyException
     -> BeanCurrentlyInCreationException
```

Không phải mọi lần đều có đúng chuỗi wrapper này; exception type và mức bọc phụ thuộc giai đoạn lỗi và cách bean được tạo. Điều quan trọng là đọc **root cause** và những đoạn cho biết “A phụ thuộc B, B phụ thuộc A”.

Ví dụ mô tả:

```text
serviceA
   |
   v
serviceB
   |
   v
serviceA  (currently in creation)
```

Nếu node vòng là **beans**, cần tìm constructors/factory-method parameters/field injection đang gây yêu cầu hai chiều. Nếu node vòng là **configuration classes** import lẫn nhau, lỗi có thể phát sinh trong configuration parser **trước khi có bean instances**. Nếu node là **configuration instance và locally defined bean**, xem constructor/`@PostConstruct` hoặc early injection vào config.

### Bảng nhận diện nhanh

| Dấu hiệu | Vòng có thể nằm ở đâu? | Hướng kiểm tra |
| --- | --- | --- |
| Thông báo `A circular @Import has been detected` | Import graph | Các `@Import` trực tiếp/meta-present, import chain |
| `BeanCurrentlyInCreationException` cho tên hai service | Constructor/factory dependency graph | Constructor của service, tham số `@Bean`, hidden service locator |
| Exception nêu configuration bean và locally defined bean | Early creation/self-reference của config | Constructor config, `@Autowired` field, `@PostConstruct` |
| Lỗi sau khi đổi `proxyBeanMethods=false`, object duplicate | Direct `@Bean` calls trong lite mode | Thay bằng factory method parameters |
| `BeanCreationException` khi @Bean static post-processor | Infrastructure bootstrap/lifecycle | Declaration static/non-static, constructor side effects |
| Lỗi khi chỉ enable một profile | Conditional bean graph | `@Profile`, bean availability trong profile đó |
| Chỉ lỗi khi test dùng subset cấu hình | Missing definitions hoặc vòng bị cấu hình test phơi lộ | Root imports, test slices, mock bean replacements |

**Không sửa theo tên exception đơn thuần.** `UnsatisfiedDependencyException` còn có thể do bean thiếu, nhiều ứng viên, sai qualifier hoặc lỗi constructor; nó không chứng minh có vòng nếu thiếu dấu hiệu cụ thể về currently-in-creation.

## 10. @Import và @ComponentScan: ranh giới cần hiểu

Một configuration class có thể được đưa vào context bằng:

- `AnnotationConfigApplicationContext(RootConfig.class)`.
- `@Import(SomeConfig.class)` từ config khác.
- Component scanning khi class có `@Configuration` (meta-annotated bằng `@Component`) nằm trong root scan.
- Cơ chế bootstrap/import selector của framework phù hợp.

Nhiều **đường dẫn đăng ký** đến cùng configuration class không nhất thiết tạo nhiều bản bean nếu Spring nhận diện cùng definition; tuy nhiên, đừng dựa vào sự trùng lặp import để tạo thứ tự hoặc “giải vòng” kỳ diệu.

### Tránh quét quá rộng trong một hệ module phức tạp

```java
@ComponentScan(basePackages = "com.company")
```

có thể đưa vào nhiều configuration classes mà ứng dụng không dự định dùng cùng nhau. Điều này đôi khi kích hoạt bean definitions mới, nhập nhằng về type hoặc các cấu hình phụ thuộc ngược mà một root import rõ ràng sẽ không chọn.

Nếu cần module boundaries chặt, hãy dùng import có chủ đích hoặc component scan giới hạn theo feature. Nhưng không nên hiểu “component scan gây vòng” trong mọi trường hợp: vòng thực sự đến từ **dependency graph** sau khi các beans/configs được chọn.

### @Import vòng không đồng nghĩa bean vòng

Một import graph không có vòng không bảo đảm bean graph không có vòng. Ngược lại, hai service beans không phụ thuộc nhau vẫn có thể nằm trong hai config classes bị import lẫn nhau.

Đó là lý do QA cần kiểm tra cả **composition** và **dependency wiring**, không chỉ cách tổ chức các tệp `@Configuration`.

## 11. Vòng service nghiệp vụ cần được phá ở tầng thiết kế

Nếu:

```text
OrderService ---> PaymentService
PaymentService ---> OrderService
```

thì việc thay `@Import`, chuyển `@Bean` sang một file khác, hay đổi `proxyBeanMethods` sẽ không tự loại bỏ nhu cầu hai chiều. Thường cần một trong các hướng:

### 11.1. Tách trách nhiệm chung thành một bean thứ ba

Trước:

```text
OrderService <----> PaymentService
```

Sau:

```text
OrderService ----> PaymentPolicy <---- PaymentService
```

Mỗi service phụ thuộc một abstraction chung `PaymentPolicy`, không cần inject service còn lại.

### 11.2. Trích coordinator/use case ở tầng cao

```text
          CheckoutCoordinator
             /        \
            v          v
       OrderService  PaymentService
```

Coordinator gọi hai services theo trình tự use case, thay vì mỗi service yêu cầu service kia được tạo trước. Cách này đặc biệt phù hợp khi vòng đến từ **orchestration** chứ không phải dependency logic thật sự.

### 11.3. Dùng event để thông báo, không gọi ngược trực tiếp

Một service thực hiện hành động rồi phát domain/application event; component khác phản ứng theo lifecycle, transaction và idempotency đã được thiết kế. Điều này có thể giảm coupling, nhưng event không miễn trừ việc thiết kế thứ tự, lỗi, tính nhất quán dữ liệu và transaction boundary.

### 11.4. Làm rõ interface hướng vào

Nếu repository cần gọi service nghiệp vụ để quyết định logic, có thể tầng dữ liệu đang phụ thuộc ngược vào tầng ứng dụng. Hãy đưa policy/rule xuống abstraction thích hợp hoặc cung cấp tham số cho repository method, không buộc repository inject toàn bộ service.

**Quan trọng:** “Phá vòng” đúng nghĩa là làm đồ thị dependency **không còn cạnh quay lại node đã có trên đường đi**, không chỉ làm Spring trì hoãn phát hiện lỗi.

## 12. @Lazy có giúp không?

`@Lazy` có thể trì hoãn việc phân giải một dependency injection point qua proxy trong một số trường hợp.

Ví dụ minh họa:

```java
class ServiceA {
    ServiceA(@Lazy ServiceB serviceB) {
        this.serviceB = serviceB;
    }
}

class ServiceB {
    ServiceB(ServiceA serviceA) {
        this.serviceA = serviceA;
    }
}
```

Nếu Spring inject proxy cho `ServiceB` thay vì tạo bean B ngay, có thể tránh yêu cầu khởi tạo B tức thời khi tạo A. Nhưng:

- Đây vẫn là **dependency logic hai chiều**.
- Khi proxy bị dereference quá sớm hoặc trong init/constructor, vòng có thể xuất hiện trở lại.
- Có các giới hạn type/proxying (ví dụ final types hoặc semantics của proxy).
- Việc trì hoãn không giải quyết coupling, ownership của state hay trách nhiệm nghiệp vụ.
- Nó không giải quyết vòng `@Import` giữa configuration classes.

**Khuyến nghị:** `@Lazy` là công cụ chọn thời điểm resolution khi dependency thật sự nên lazy, **không phải cách mặc định để che mọi circular reference**.

## 13. ObjectProvider và Provider: trì hoãn lookup có điều kiện

Nếu một service chỉ cần cộng tác viên tại **thời điểm thực thi một thao tác**, có thể tiêm:

```java
import org.springframework.beans.factory.ObjectProvider;

class DeferredClient {
    private final ObjectProvider<RemoteGateway> gatewayProvider;

    DeferredClient(ObjectProvider<RemoteGateway> gatewayProvider) {
        this.gatewayProvider = gatewayProvider;
    }

    String sendLater(String message) {
        RemoteGateway gateway = gatewayProvider.getObject();
        return gateway.send(message);
    }
}
```

Đây là minh họa cần `RemoteGateway` interface. `ObjectProvider` cho phép lấy bean khi method `sendLater` được gọi, thay vì buộc resolve trong constructor.

Nhưng:

- Nếu `getObject()` được gọi trong constructor hoặc `@PostConstruct`, bạn vẫn có thể tái tạo early creation cycle.
- Nếu vòng chỉ được tránh vì các method chưa được gọi cùng lúc, cấu trúc kiến trúc có thể vẫn bất ổn.
- `getIfAvailable()` và `getIfUnique()` phù hợp cho **dependency tùy chọn thật**, không nên dùng để âm thầm bỏ qua dependency bắt buộc nhằm làm context start thành công.
- Nó không thay thế root composition cho import graph.
- Nếu bean đang là prototype/scoped, việc lookup theo thời điểm có tác động tới lifecycle/identity cần thiết kế riêng.

**Chỉ dùng provider** khi dynamic/lazy lookup là một yêu cầu thật của nghiệp vụ/hạ tầng, không phải để xóa thông báo lỗi vòng mà không thay đổi thiết kế.

## 14. @DependsOn không phải công cụ phá vòng

`@DependsOn` bổ sung **ràng buộc thứ tự khởi tạo bean**. Ví dụ:

```java
@Bean
@DependsOn("schemaValidator")
ReportService reportService() {
    return new ReportService();
}
```

Ý nghĩa: Bean `schemaValidator` phải được khởi tạo trước khi Spring khởi tạo `reportService`, bên cạnh các dependency trực tiếp của report service. Nó không cung cấp instance thay thế cho một constructor cần dependency, và không làm vòng A→B→A trở thành có thể khởi tạo.

Nếu:

```text
A --dependsOn--> B
B --dependsOn--> A
```

thì bạn đã tạo **vòng thứ tự**. Spring không thể vừa khởi tạo A trước B vừa khởi tạo B trước A. Việc đổi sang `@DependsOn` thay vì constructor injection không phải giải pháp khi hai bean thật sự cần reference tới nhau.

Dùng `@DependsOn` cho yêu cầu ordering có nghĩa thực sự, ví dụ một component hạ tầng cần bước validation đã hoàn thành; đừng dùng nó như orchestration nghiệp vụ hay dependency inversion.

## 15. Có nên bật cho phép circular references?

Spring Boot có thuộc tính:

```properties
spring.main.allow-circular-references=true
```

Từ các phiên bản Spring Boot hiện đại, mặc định tùy chọn này thường là `false`. Tuy nhiên, bài này phân biệt rõ **Spring Framework Core** và **Spring Boot**:

- `spring.main.allow-circular-references` là **cấu hình Spring Boot**, không phải cú pháp cấu hình trực tiếp của `AnnotationConfigApplicationContext` trong Spring Framework thuần.
- Một số circular dependencies thông qua setter/field injection có thể được Spring BeanFactory giải quyết bằng early references khi cơ chế hỗ trợ cho phép.
- **Constructor-to-constructor cycles** như A(B)→B(A) vẫn thường bất khả giải, kể cả khi đã bật cho phép circular references.
- Circular `@Import` của configuration parser không phải điều có thể sửa chỉ bằng bean factory flag.
- Early references có thể tương tác khó với AOP proxies, init callbacks và incomplete state.

**Kết luận:** Đừng bật cờ này như “fix” mặc định. Nó là biện pháp tương thích tạm thời trong một số migration, cần test và kế hoạch refactor. Về lâu dài, sửa **hướng dependency** vẫn tốt hơn.

### Nếu dùng plain Spring Framework

Một `DefaultListableBeanFactory`/`AbstractAutowireCapableBeanFactory` có các tùy chọn circular reference handling ở tầng bean factory. Đây là API cấu hình hạ tầng, không nên là bước đầu tiên trong khắc phục thiết kế dependency vòng.

Với ví dụ runnable mục 8, không cần bật bất kỳ cơ chế cho phép vòng nào; bean graph đã không có chu trình.

## 16. Một số bẫy chuyên biệt của @Configuration

### 16.1. Constructor injection của config kéo theo bean do chính nó định nghĩa

```java
@Configuration
class FeatureConfig {

    FeatureConfig(FeatureRegistry registry) {
        // ...
    }

    @Bean
    FeatureRegistry registry() {
        return new FeatureRegistry();
    }
}
```

Nếu không có bean `FeatureRegistry` khác hợp lệ, constructor config đòi bean mà nó phải được tạo xong mới có thể xây. Đây là dấu hiệu self-reference cycle. Đừng chuyển injection xuống field `@Autowired` rồi xem là đã chắc chắn giải quyết: field injection có thể gây early initialization và incomplete state.

### 16.2. @PostConstruct của configuration class gọi @Bean

Như đã nêu ở mục 6, Spring docs lưu ý rõ việc truy cập locally defined beans trong `@PostConstruct` của cùng configuration class có thể khiến circular reference. Đưa việc chuẩn bị trạng thái vào lifecycle bean đích hoặc một startup coordinator riêng.

### 16.3. BeanFactoryPostProcessor và BeanPostProcessor

Những post-processors này tham gia **rất sớm** vào container bootstrap. Nếu chúng được định nghĩa bằng **non-static `@Bean`**, việc tạo chúng có thể buộc Spring instantiate configuration class sớm, khiến `@Autowired`/`@Value` trên chính configuration class chưa được xử lý như mong đợi.

Một số post-processor beans thường nên dùng **static `@Bean`** để không cần tạo instance config sớm. Nhưng chỉ làm vậy khi bạn đang định nghĩa loại bean infrastructure đó, không phải trên mọi service.

### 16.4. @Configuration(proxyBeanMethods=false) không giải quyết vòng import

Cờ này tắt CGLIB interception của `@Bean` methods; nó không sửa việc ConfigA import ConfigB rồi ConfigB import ConfigA. Cần sửa **composition graph** riêng.

### 16.5. @ImportResource cũng không làm vòng tự biến mất

Nếu XML bean definitions tạo bean A phụ thuộc B, bean B lại phụ thuộc A qua constructors, thì nhập XML bằng `@ImportResource` vào Java context vẫn tạo **vòng dependency beans**. Thay vị trí metadata từ XML sang Java hoặc ngược lại không tự thay đổi đồ thị nhu cầu giữa các instances.

## 17. Cách debug vòng từ một ứng dụng lớn

Khi nhìn thấy thông báo circular dependency, nên làm theo một quy trình hữu hạn:

1. **Xác định thời điểm lỗi:** configuration parsing hay bean instantiation?
2. **Ghi lại tên node:** config class names, bean names, factory methods được liệt kê trong exception.
3. **Vẽ cạnh phụ thuộc:** `@Import`; config constructor; `@Bean` method parameter; service constructor; `@DependsOn`; lookup trong init.
4. **Phân loại vòng:** import graph, config instance graph hay application bean graph.
5. **Tìm nguyên nhân cấu trúc:** module biết lẫn nhau, trách nhiệm chung chưa được trích, bean tự phụ thuộc vào factory config của nó, hoặc code gọi sớm.
6. **Chọn refactor nhỏ nhất có ý nghĩa:** root composition, phương thức `@Bean` nhận tham số, trích interface/coordinator, chuyển startup work khỏi config init.
7. **Chạy context test:** tạo `AnnotationConfigApplicationContext` với đúng root, không dựa vào unit test chỉ dùng `new`.
8. **Kiểm tra identity:** instance được consumer giữ phải giống managed singleton nếu scope yêu cầu.
9. **Test cấu hình theo profile:** một graph có thể chỉ có vòng trong một profile cụ thể.
10. **Chỉ cân nhắc workaround lazy/provider** nếu dependency có ngữ nghĩa lazy thật và đã hiểu rủi ro.

### Đừng chỉ xóa một cạnh để context khởi động

Ví dụ bỏ dependency trong constructor nhưng chuyển sang `ApplicationContext.getBean` ở một method init có thể chỉ dời lỗi sang thời điểm khác. Code trở nên khó test hơn và vòng logic vẫn tồn tại.

Một refactor đúng nên khiến người đọc có thể nhìn vào constructors/factory methods và thấy **đồ thị có hướng không quay về chính nó**, hoặc có một abstraction/coordinator rõ ràng giải thích vì sao không cần hai chiều.

## 18. Kiểm thử chống regression

Một integration test nhỏ có thể khởi tạo root configuration rồi lấy bean:

```java
// Đoạn minh họa, không phải file test standalone.
try (var context =
        new AnnotationConfigApplicationContext(RootConfig.class)) {
    CatalogRepository repository =
            context.getBean(CatalogRepository.class);
    CatalogService service =
            context.getBean(CatalogService.class);

    if (service.repository() != repository) {
        throw new AssertionError(
                "Service must use the managed repository singleton");
    }
}
```

Trong project ví dụ, các class là nested classes của `CycleFreeConfigDemo`, vì thế khi viết ở file test độc lập cần import `demo.CycleFreeConfigDemo.RootConfig` và các nested types phù hợp.

### Kiểm tra chỉ đúng các modules mong muốn đã được nạp

Với ứng dụng thật, tạo context từ **root cấu hình duy nhất** và kiểm tra số bean names cho các interfaces chính. Nếu một class config vô tình được import thêm module phụ, test sẽ phát hiện số ứng viên thay đổi hoặc bean graph thất bại.

### Kiểm tra các profiles khác nhau

Nếu module có `@Profile("dev")` hoặc `@Profile("prod")`, mỗi profile có thể dẫn tới tập beans khác nhau. Hãy tạo context test riêng cho những cấu hình có rủi ro, đảm bảo đủ dependency, không có vòng và không import duplicate application modules.

### Cố ý tái hiện lỗi ở môi trường test

Không đưa các phản ví dụ vòng vào root production. Có thể tạo configuration classes riêng trong test để assert rằng context **fail fast** với constructor cycle hoặc circular import. Khi kiểm tra lỗi, đừng phụ thuộc quá chặt vào toàn bộ chuỗi stack trace, vì wrapper messages có thể thay đổi theo phiên bản Spring.

### Kiểm tra init callbacks

Nếu từng có self-reference trong `@PostConstruct` của config, regression test nên chứng minh init đã được chuyển sang managed bean hoặc coordinator và không ép context tạo bean quá sớm.

### Kiểm tra fail-fast của dependency bắt buộc

Một repository thiếu trong cấu hình test nên khiến tạo service bean thất bại rõ. Đừng biến dependency bắt buộc thành `Optional`/`ObjectProvider.getIfAvailable()` rồi im lặng bỏ qua chỉ để context “xanh”.

## 19. Câu hỏi thường gặp

### Có bắt buộc mọi module chỉ có một configuration class?

Không. Một module có thể có vài configurations theo trách nhiệm khác nhau. Điều quan trọng là **định hướng import/ownership rõ**, không phải số class tuyệt đối.

### Có phải hai config import nhau luôn tạo vòng bean constructor?

Không. Import graph là cấu trúc xử lý metadata, bean dependency graph là cấu trúc tạo instances. Circular import có thể lỗi ở configuration parser, dù bean constructors không phụ thuộc nhau.

### Nếu root import nhiều configs thì tạo nhiều BeanFactory?

Không. Khi một `AnnotationConfigApplicationContext` xử lý `RootConfig` và các `@Import` của nó, những bean definitions ở trong **cùng container** trừ khi chính code tạo những contexts riêng một cách có chủ đích.

### Nếu ServiceConfig dùng Repository bean thì có phải import RepositoryConfig trực tiếp không?

Không, nếu một root đã import cả hai. Tham số `@Bean service(Repository repository)` cho phép Spring inject bean cùng context theo type.

### Dùng full @Configuration gọi bean method khác có sai không?

Không mặc định sai. Full configuration cho phép inter-bean references thông qua CGLIB enhancement khi gọi trên managed config instance. Tuy nhiên, **method parameter injection** vẫn dễ đọc, ít coupling hơn và an toàn nếu sau này chuyển sang lite mode.

### Có cần @DependsOn để ép repo tạo trước service?

Nếu service factory method nhận `Repository` bắt buộc, Spring đã biết dependency và tạo/resolve nó khi cần. Không cần thêm `@DependsOn` chỉ để lặp lại quan hệ dependency có sẵn.

### Bật allow-circular-references có giúp constructor A(B), B(A) không?

Không nên trông chờ. Constructor cycles cần cả hai instances trước khi một instance có thể hoàn tất; đây là tình huống bất khả giải thông thường ngay cả khi một số vòng setter/field có thể được cơ chế early references xử lý.

### Có nên inject ApplicationContext vào config để tự lấy bean?

Thường không. Nó che dependency trong code và khiến test khó hơn. Tại factory method, hãy yêu cầu bean bằng tham số. Nếu cần dynamic lookup thật, dùng `ObjectProvider<T>` ở bean có trách nhiệm runtime tương ứng, không ở config init.

### Nếu một config bị @Import từ hai nhánh?

Spring có cơ chế xử lý configuration classes và imported definitions, nhưng không nên dùng đường import trùng để điều khiển thứ tự hoặc duplicate instances. Hãy thử với root context thật và làm rõ ownership. Một root import danh sách module trực tiếp thường dễ kiểm tra hơn.

### Một vòng chỉ xuất hiện ở production vì sao?

Khác biệt profiles, conditional beans, component scan root, auto-configuration, config properties, hoặc một implementation phụ thuộc ngược khác có thể tạo dependency graph mới. Integration tests cần đại diện các biến thể cấu hình quan trọng.

## 20. Bài tập có đáp án

### Bài 1 — Nhận diện vòng import

Hai class:

```java
@Configuration
@Import(BConfig.class)
class AConfig {}

@Configuration
@Import(AConfig.class)
class BConfig {}
```

Đây là loại vòng gì? Sửa thế nào để hai module đều được nạp?

**Đáp án:** Vòng **import graph**. Bỏ imports hai chiều, tạo `RootConfig` với `@Import({AConfig.class, BConfig.class})`, hoặc chọn một direction hợp lý nếu thật sự có hierarchy. Không cần inject instance config.

### Bài 2 — Nhận diện vòng constructor giữa config

```java
@Configuration
class AConfig {
    AConfig(BConfig b) {}
}

@Configuration
class BConfig {
    BConfig(AConfig a) {}
}
```

Vì sao Spring không khởi tạo được hai config instances?

**Đáp án:** Mỗi constructor yêu cầu instance của class kia đã tồn tại; BeanFactory không có điểm bắt đầu để hoàn tất construction. Refactor để configs không inject nhau, sử dụng bean method parameters cho dependency nghiệp vụ.

### Bài 3 — Module service dùng repository bean

Bạn có `RepositoryConfig` tạo `AccountRepository` và `ServiceConfig` tạo `AccountService`. Làm sao tránh `ServiceConfig` phải giữ `RepositoryConfig` field?

**Đáp án:**

```java
@Configuration(proxyBeanMethods = false)
class ServiceConfig {
    @Bean
    AccountService accountService(AccountRepository repository) {
        return new AccountService(repository);
    }
}
```

Root import cả hai config classes. Spring inject repository bean qua tham số factory method.

### Bài 4 — @PostConstruct của config

Một `@Configuration` tự gọi `dataSource()` trong `@PostConstruct`, trong khi `dataSource()` cũng là non-static `@Bean` method của nó. Vì sao rủi ro?

**Đáp án:** Config instance còn trong giai đoạn initialization; bean do method nó sản xuất có thể cần chính config instance đã được tạo/hoàn tất, dẫn tới self-reference/early initialization và `BeanCurrentlyInCreationException`. Nên dùng bean-local `initMethod`/`@PostConstruct` của bean đích hoặc một startup coordinator.

### Bài 5 — Dùng @Lazy để “sửa vòng”

Một service A cần B trong constructor, B cần A trong constructor. Đánh `@Lazy` vào injection point B có phải đã sửa dependency design?

**Đáp án:** Không. Nó có thể trì hoãn phân giải thông qua proxy ở một số trường hợp, nhưng quan hệ hai chiều vẫn tồn tại và có thể lỗi khi dereference sớm. Tốt hơn là trích abstraction hoặc coordinator.

### Bài 6 — @DependsOn

`A` có `@DependsOn("b")` và `B` có `@DependsOn("a")`. Điều gì xảy ra về logic ordering?

**Đáp án:** Không có thứ tự khởi tạo thỏa cả hai yêu cầu. Đây là vòng ordering; bỏ cạnh sai hoặc mô hình hóa quan hệ dependency theo hướng thực tế.

### Bài 7 — Lite config

```java
@Configuration(proxyBeanMethods = false)
class Config {
    @Bean
    Repo repo() { return new Repo(); }

    @Bean
    Service service() { return new Service(repo()); }
}
```

Rủi ro gì và sửa thế nào?

**Đáp án:** `repo()` gọi trực tiếp là Java factory call, có thể tạo repo ngoài managed bean identity. Sửa thành `Service service(Repo repo) { return new Service(repo); }`. Việc sửa này bảo toàn DI semantics nhưng nếu `Repo` phụ thuộc ngược `Service` thì vẫn cần phá vòng bean thật.

### Bài 8 — Đọc sơ đồ

```text
RootConfig -> InfraConfig
RootConfig -> RepositoryConfig
RootConfig -> ServiceConfig

Bean graph:
Facade -> Service -> Repository
                  -> Clock
```

Có vòng không?

**Đáp án:** Không trong các cạnh đã mô tả. Root import một chiều, bean graph không quay lại node cũ.

### Bài 9 — Spring Boot

Ứng dụng Spring Boot đặt `spring.main.allow-circular-references=true` để giải lỗi giữa hai services constructor-inject lẫn nhau. Có nên coi đây là cách sửa bền vững?

**Đáp án:** Không. Một số vòng constructor vẫn bất khả giải; cờ chỉ liên quan cơ chế xử lý một số circular bean references, không sửa import graph hay kiến trúc. Phải refactor dependencies.

### Bài 10 — Thiết kế đúng cho import composition

Hệ thống có `DbConfig`, `MessagingConfig`, `OrdersConfig`; `OrdersService` cần `DataSource` và `MessagePublisher`. Hãy đề xuất tổ chức.

**Đáp án định hướng:**

```java
@Configuration(proxyBeanMethods = false)
@Import({
    DbConfig.class,
    MessagingConfig.class,
    OrdersConfig.class
})
class RootConfig {
}

@Configuration(proxyBeanMethods = false)
class OrdersConfig {
    @Bean
    OrdersService ordersService(
            DataSource dataSource,
            MessagePublisher publisher) {
        return new OrdersService(dataSource, publisher);
    }
}
```

Đây là mẫu cần các kiểu được định nghĩa riêng. `OrdersConfig` không import ngược Db/Messaging config chỉ để lấy instance. Dependency được giải quyết từ BeanFactory chung theo parameters. Nếu `DbConfig` hoặc `MessagingConfig` phụ thuộc ngược `OrdersService`, đó là tín hiệu cần điều chỉnh trách nhiệm/abstraction.

### Bài 11 — Thứ tự bean creation

Nếu `@Bean Service service(Repository repo)` được Spring gọi, có cần khai báo `@DependsOn("repo")` để repo có trước không?

**Đáp án:** Không khi `Repository` được tiêm như tham số bắt buộc; BeanFactory giải quyết repository trước khi có thể gọi factory method tạo Service.

### Bài 12 — Test đồ thị

Bạn được giao refactor ba configs để loại vòng. Test tối thiểu cần kiểm tra gì?

**Đáp án:** Tạo context từ root mới; lấy các core bean types; assert dependency identity nếu singleton; kiểm tra tổng số bean names và hành vi service; thử profile/conditional variants liên quan; xác nhận không cần bật allow-circular-references hoặc dùng providers để che thiếu dependency.

## 21. Checklist review trước khi ghép cấu hình

- [ ] Có một hoặc một số **entry-point/root configs** rõ ràng, không tạo import ngược vô tình.
- [ ] Mỗi module config chủ yếu khai báo bean definitions thuộc trách nhiệm riêng.
- [ ] Không inject `RepositoryConfig` vào `ServiceConfig` chỉ để gọi phương thức `@Bean`.
- [ ] Khi bean service cần repository, dùng constructor hoặc **factory-method parameter injection**.
- [ ] Không inject một bean vào constructor config nếu chính config này phải sản xuất bean đó.
- [ ] Không gọi locally defined bean từ `@PostConstruct` của config class.
- [ ] Những `BeanFactoryPostProcessor`/`BeanPostProcessor` tạo quá sớm đã được cân nhắc `static @Bean` khi phù hợp.
- [ ] Full/lite configuration semantics được hiểu; không direct-call `@Bean` trong lite để lấy managed object.
- [ ] Không dùng `@DependsOn` để che vòng dependency thực.
- [ ] `@Lazy`/`ObjectProvider` chỉ xuất hiện khi có lazy/dynamic lookup requirement thật, không che vấn đề kiến trúc.
- [ ] Không dựa vào `spring.main.allow-circular-references` làm cách sửa mặc định.
- [ ] Context startup test xác nhận không có `BeanCurrentlyInCreationException`.
- [ ] Đồ thị dependencies theo từng profile/conditional config quan trọng đã được kiểm tra.
- [ ] Không tạo ApplicationContext phụ hoặc configuration instances bằng `new` trong `@Bean`.
- [ ] Mỗi resource có lifecycle rõ, không khởi tạo hai lần do direct method calls.
- [ ] Bean definitions không bị trùng tên/kiểu do import hoặc scan ngoài ý muốn.
- [ ] Có integration test cho bean identity và đường use case chính sau refactor.

## Kết luận

**Tránh phụ thuộc vòng giữa các lớp cấu hình** không có nghĩa phải dồn toàn bộ cấu hình vào một class. Điều cần làm là **tách cấu trúc ghép cấu hình khỏi cấu trúc dependency của bean**:

- `@Import` giúp **root composition** đưa nhiều cấu hình vào cùng context theo một hướng rõ ràng.
- `@Bean` method parameters cho phép bean ở một module nhận bean từ module khác **mà configuration classes không cần inject nhau**.
- Constructor/`@PostConstruct` trên `@Configuration` phải tránh tham chiếu quá sớm tới những bean do chính instance config đó sản xuất.
- Vòng constructor giữa application beans phải được sửa ở **tầng thiết kế service/abstraction/coordinator**, không chỉ đổi file config.
- `@Lazy`, `ObjectProvider`, `@DependsOn` và circular-reference flags có phạm vi riêng; chúng không thay thế cho đồ thị dependency đúng.

**Mẫu đáng nhớ:** root import các module; mỗi module khai báo factory methods; dependency giữa beans được Spring phân giải bằng tham số; không một module config nào phải gọi ngược sang config instance khác chỉ để lấy object.

## Tài liệu tham khảo chính thức

- [Spring Framework 6.2 — Composing Java-based Configurations](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/composing-configuration-classes.html): `@Import`, dependency giữa imported configurations, cảnh báo early config initialization và `@PostConstruct`.
- [Spring Framework 6.2 — Dependency Injection and Circular Dependencies](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-collaborators.html): constructor cycle, bean creation và ý nghĩa của early references.
- [Spring Framework 6.2 — Basic Concepts: @Bean and @Configuration](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/basic-concepts.html): full/lite configuration modes.
- [Spring Framework 6.2 — Using the @Configuration Annotation](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/configuration-annotation.html): inter-bean method references và CGLIB enhancement.
- [Spring Framework 6.2 — Using the @Bean Annotation](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/bean-annotation.html): factory method parameters, type metadata và lifecycle.
- [Spring Boot — Common Application Properties](https://docs.spring.io/spring-boot/appendix/application-properties/): `spring.main.allow-circular-references` thuộc Boot, không thuộc Spring Core configuration annotation.


</details>

- [Quay lại đầu trang](#back-to-top)
