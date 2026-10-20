<a id="back-to-top"></a>

# Ứng dụng đầu tiên

## Menu
- [Chuẩn bị thư viện spring-context và phiên bản tương thích](#first-application-001)
- [Tạo lớp nghiệp vụ và hợp đồng cho đối tượng cộng tác](#first-application-002)
- [Khai báo cấu hình tối thiểu bằng @Configuration và @Bean](#first-application-003)
- [Khởi tạo AnnotationConfigApplicationContext](#first-application-004)
- [Lấy bean theo kiểu và thực hiện một luồng xử lý](#first-application-005)
- [Quan sát đối tượng được tạo và phụ thuộc được cung cấp](#first-application-006)
- [Đóng context bằng try-with-resources](#first-application-007)
- [So sánh ứng dụng có container với cách lắp ghép thủ công](#first-application-008)

## <a id="first-application-001">Chuẩn bị thư viện spring-context và phiên bản tương thích</a>

<details>
<summary>Xem chi tiết</summary>

# Chuẩn bị thư viện `spring-context` và phiên bản tương thích

## 1. Hiểu đúng nhiệm vụ thiết lập

Để viết ứng dụng Spring Core Container đầu tiên bằng Java 17+, bạn cần **JDK**, **công cụ build**, **dependency `spring-context`**, rồi một chương trình tạo `ApplicationContext`. Không cần Spring Boot, máy chủ HTTP hay database để thử IoC/DI.

Tọa độ dependency Maven thường dùng là:

~~~xml
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-context</artifactId>
    <version>7.0.9</version>
</dependency>
~~~

Ở đây `org.springframework` là **groupId**, `spring-context` là **artifactId**, `7.0.9` là **version**. Ba phần này định vị một artifact đã phát hành trên repository như Maven Central. Tên artifact **khác** Java package `org.springframework.context` và **khác** tên class `ApplicationContext`.

Bản `7.0.9` được cố định làm ví dụ ổn định, đã có trong tài liệu chính thức của Spring Framework tại thời điểm soạn bài. Khi dùng sản phẩm thực tế, kiểm tra release đang được hỗ trợ, vá lỗi bảo mật và chạy test sau khi nâng; không coi một số phiên bản trong tài liệu học là phiên bản phải giữ mãi.

**Kết quả cần đạt:** biên dịch được import `org.springframework.context.annotation.*`, mở được `AnnotationConfigApplicationContext`, đăng ký `@Bean`, lấy bean qua `getBean`, kiểm tra dependency tree và biết xử lý lỗi JDK/classpath/version.

## 2. Vì sao chọn đúng `spring-context` thay vì tải mọi JAR?

Spring Framework có nhiều module tách riêng, được phát hành thành những artifact Maven/Gradle:

| Artifact | Trách nhiệm chính | API/khái niệm dễ gặp |
| --- | --- | --- |
| `spring-core` | Tiện ích cơ sở, resource abstraction và hạ tầng | `Resource`, `ClassPathResource` |
| `spring-beans` | Bean factory, bean definitions, cấu hình và dependency wiring | `BeanFactory`, `BeanDefinition` |
| **`spring-context`** | Container ở cấp ứng dụng và cấu hình annotation | `ApplicationContext`, `AnnotationConfigApplicationContext`, `@Configuration`, `@Bean` |
| `spring-expression` | Ngôn ngữ biểu thức SpEL | `ExpressionParser` |
| `spring-aop` | Các cơ chế proxy/AOP cần thiết khi chức năng liên quan được sử dụng | Proxy infrastructure |
| `spring-webmvc` | MVC theo Servlet cho ứng dụng HTTP | `DispatcherServlet` |
| `spring-tx` | Hạ tầng quản lý transaction | `PlatformTransactionManager` |

Với demo Core Container chỉ có Java configuration và DI, hãy khai báo **một dependency trực tiếp `spring-context`**. Maven/Gradle đọc metadata POM của artifact để tự resolve các **dependency transitive bắt buộc** (trong đó có các module hạ tầng như `spring-beans`, `spring-core`). Không cần tự liệt kê lại mọi module để “chắc ăn”, cũng không nên tự tải một vài JAR lẻ rồi tùy tiện ghép classpath.

**`spring-core` một mình không chứa API `ApplicationContext`.** `spring-context-support` cũng không phải artifact bắt buộc cho một demo `@Bean` thông thường. Spring không có một JAR `spring-all` chứa trọn mọi khả năng.

Những thư viện như `spring-webmvc`, `spring-jdbc`, `spring-security`, bộ database driver hoặc embedded server **không được mặc nhiên có mặt** chỉ vì bạn đã thêm `spring-context`.

### Dependency trực tiếp và dependency transitive

- **Direct dependency:** khai báo ngay trong `pom.xml` hoặc `build.gradle`.
- **Transitive dependency:** được artifact phụ thuộc khai báo và build tool giải quyết theo graph.
- **Resolved dependency:** phiên bản cuối cùng mà build tool chọn sau khi xét version, BOM, exclusions và xung đột.

Nhìn thấy `spring-beans` trong `dependency:tree` **không có nghĩa** bạn đã khai báo nó trực tiếp. Ngược lại, nhìn thấy JAR đã tải về máy **không đảm bảo** JAR nằm trên compile/runtime classpath của module đang làm.

## 3. Đối chiếu phiên bản Spring, Java và hệ sinh thái

Chọn version theo **toàn bộ ứng dụng**, không chỉ theo một con số trên Maven Central.

| Mục | Spring Framework 6.2 | Spring Framework 7.0 |
| --- | --- | --- |
| JDK baseline | Java 17 | **Java 17** |
| Bản GA có thể đối chiếu | `6.2.19` | `7.0.9` |
| Hệ tích hợp thường gặp | Spring Boot 3.x tương ứng | Spring Boot 4.x tương ứng |
| Jakarta | Thế hệ Jakarta của Framework 6.x | Baseline **Jakarta EE 11** |
| Cách chọn cho ví dụ | Có thể dùng nếu các module liên quan cùng dòng 6.2 | Dùng **7.0.9** với Java 17 |

Spring 7.0 vẫn hỗ trợ **Java 17 là mức tối thiểu**, dù dự án Spring khuyến nghị sử dụng JDK LTS mới hơn khi phù hợp. Vì vậy việc `maven.compiler.release=17` là hợp lệ với ví dụ API `ApplicationContext` trên Spring 7.0.

**Quy tắc tương thích quan trọng:**

1. Nếu thêm nhiều Spring artifacts trực tiếp (ví dụ `spring-context`, `spring-jdbc`, `spring-tx`), quản lý chúng bằng **cùng bản phát hành Framework** hoặc một **BOM** phù hợp.
2. **Không ép** `spring-beans:6.2.x` dưới `spring-context:7.0.9` chỉ để giải quyết một conflict Maven; có thể sinh lỗi linkage lúc chạy.
3. Khi nâng **6.2 → 7.0**, đọc migration/release notes, đối chiếu API được bỏ, Jakarta EE 11, servlet/integration libraries. “Đều chạy Java 17” **không chứng minh** toàn bộ dependency Java cũ tương thích.
4. Nếu dự án dùng **Spring Boot**, hãy để BOM/parent/plugin của Boot quản lý phiên bản Spring Framework tương ứng. Đừng tự thêm một version `spring-context` lệch dòng của Boot khi không hiểu toàn bộ hệ quả.
5. Bản GA phát hành lên Maven Central thường không cần cấu hình repository snapshot/milestone. Tránh `SNAPSHOT`, `-M...`, `-RC...` trong bài nhập môn trừ khi chủ đích thử bản preview.

**Framework khác Boot:** `spring-context` là module nền IoC của Spring Framework; `spring-boot-starter` là tập dependency cho một loại dự án Spring Boot. Bài này dùng Framework trực tiếp để thấy container được tạo ra như thế nào.

## 4. Kiểm tra JDK và công cụ build

Mở terminal mới và chạy:

~~~bash
java -version
javac -version
mvn -version
~~~

Kiểm tra `java` và `javac` thuộc một JDK **17 hoặc cao hơn**. `mvn -version` còn hiển thị Java home mà Maven thực tế sử dụng; không được kết luận Maven đang chạy Java 17 chỉ vì IDE đang chọn Java 17.

Trên Linux/macOS có thể kiểm tra:

~~~bash
echo "$JAVA_HOME"
which java
which javac
~~~

Trên Windows PowerShell:

~~~powershell
$env:JAVA_HOME
Get-Command java
Get-Command javac
~~~

Output của các lệnh kiểm tra này **phụ thuộc máy**, không có stdout cố định. Khi sửa `JAVA_HOME` hoặc `PATH`, mở terminal mới và kiểm tra lại.

**JDK** là bộ công cụ/SDK; **Maven** là build tool; **Maven Compiler Plugin** gọi compiler Java; **JAR Spring** là thư viện được biên dịch sẵn; **Maven Central** là repository cung cấp artifact. Chúng không thay thế cho nhau.

Nên sử dụng Maven Wrapper (`mvnw`) hoặc Gradle Wrapper (`gradlew`) nếu dự án đã cung cấp: wrapper ghim phiên bản build tool cho cả nhóm. Vì ví dụ sau là dự án tạo mới, dùng lệnh `mvn` sau khi cài Maven 3.9+.

## 5. Project Maven Java 17 hoàn chỉnh với `spring-context`

Tạo đúng cấu trúc:

~~~text
spring-context-first/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── SpringContextFirst.java
~~~

### 5.1. `pom.xml`

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-context-first</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <spring.framework.version>7.0.9</spring.framework.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>${spring.framework.version}</version>
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
                    <mainClass>demo.SpringContextFirst</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

**Giải thích POM:**

- `spring-context` dùng scope Maven mặc định là **compile**: Spring API có mặt khi biên dịch và runtime.
- `maven.compiler.release=17` yêu cầu compiler nhắm API/bytecode Java 17, tránh vô tình gọi API JDK mới hơn trong mã source.
- Property `spring.framework.version` gom version Framework về một chỗ. Khi có nhiều Spring module, BOM còn phù hợp hơn việc sao chép version.
- `maven-compiler-plugin` thực thi bước biên dịch; `exec-maven-plugin` khởi chạy main class cùng dependency classpath.
- Không bỏ `version` trong dependency này trừ khi POM thực sự kế thừa/import **dependency management** thích hợp.

Không cần khai báo riêng `spring-core`, `spring-beans` hoặc `spring-expression` trong demo nếu Maven giải quyết được graph qua `spring-context`.

### 5.2. `src/main/java/demo/SpringContextFirst.java`

~~~java
package demo;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public final class SpringContextFirst {
    private SpringContextFirst() {
    }

    interface Formatter {
        String greet(String name);
    }

    static final class EnglishFormatter implements Formatter {
        @Override
        public String greet(String name) {
            return "Hello, " + name + "!";
        }
    }

    static final class GreetingService {
        private final Formatter formatter;

        GreetingService(Formatter formatter) {
            this.formatter = formatter;
        }

        String greet(String name) {
            return formatter.greet(name);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class AppConfig {
        @Bean
        Formatter formatter() {
            return new EnglishFormatter();
        }

        @Bean
        GreetingService greetingService(Formatter formatter) {
            return new GreetingService(formatter);
        }
    }

    public static void main(String[] args) {
        try (var context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {
            GreetingService first =
                    context.getBean(GreetingService.class);

            System.out.println("message=" + first.greet("An"));
            System.out.println("sameSingleton="
                    + (first == context.getBean(GreetingService.class)));
            System.out.println("context="
                    + context.getClass().getSimpleName());
        }
    }
}
~~~

**Vì sao đây là kiểm chứng thực sự?** Khi thiếu `spring-context`, compiler không tìm được các import Spring. Khi đã cấu hình đúng, `AnnotationConfigApplicationContext` đọc `AppConfig`, đăng ký `formatter` và `greetingService`; Spring resolve bean `Formatter` để truyền cho `@Bean greetingService(...)`. Scope mặc định singleton khiến hai lần lookup trả cùng reference trong context này.

`@Configuration(proxyBeanMethods=false)` dùng được vì hai factory methods không tự gọi trực tiếp nhau. `try-with-resources` đóng context đúng cách. Không cần Spring Boot, HTTP server, `@Autowired` field hoặc component scan cho demo Java configuration có khai báo `@Bean` tường minh.

### 5.3. Build và chạy

Đứng ở thư mục `spring-context-first` chứa `pom.xml`:

~~~bash
mvn -q clean compile exec:java
~~~

Maven tải dependency (ở lần đầu), dùng Maven Compiler Plugin gọi **`javac`** và chạy chương trình với classpath Spring. Lệnh `javac SpringContextFirst.java` trực tiếp **không đủ** nếu bạn chưa tự cung cấp đầy đủ classpath JAR Spring.

**Ba dòng stdout do chính code ứng dụng in** (không tính log Maven hoặc logging backend):

~~~text
message=Hello, An!
sameSingleton=true
context=AnnotationConfigApplicationContext
~~~

Nếu ứng dụng in thêm log thư viện trong môi trường riêng, phân biệt log đó với đúng ba lệnh `System.out.println` trong `main`.

## 6. Setup bằng Gradle thay cho Maven

Nếu dự án dùng Gradle, không cần đồng thời tạo `pom.xml` và `build.gradle`. Ví dụ sau sử dụng **nguyên `SpringContextFirst.java` ở mục 5.2** làm source, nên output ứng dụng giống mục 5.3.

~~~text
spring-context-gradle/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/SpringContextFirst.java
~~~

Tệp `settings.gradle`:

~~~groovy
rootProject.name = 'spring-context-gradle'
~~~

Tệp `build.gradle` (Groovy DSL):

~~~groovy
plugins {
    id 'java'
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-context:7.0.9'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass = 'demo.SpringContextFirst'
}
~~~

Dùng bản Gradle hỗ trợ Java 17 và cấu hình JDK/toolchain tương thích. Nếu **đã có Gradle Wrapper**, trên Linux/macOS chạy:

~~~bash
./gradlew run
~~~

Trên Windows PowerShell:

~~~powershell
.\gradlew.bat run
~~~

Nếu project mới **chưa có wrapper**, hãy cài Gradle tương thích rồi chạy `gradle run` hoặc tạo wrapper; không giả định file `gradlew` tự xuất hiện. Task `run` của Application Plugin thiết lập runtime classpath gồm dependency direct/transitive của dự án.

**Stdout của code ứng dụng**:

~~~text
message=Hello, An!
sameSingleton=true
context=AnnotationConfigApplicationContext
~~~

Gradle có thể in thêm trạng thái task và build ngoài ba dòng trên.

## 7. Quan sát dependency graph và BOM

Sau khi build Maven thành công, xem Spring artifacts mà dự án resolve:

~~~bash
mvn dependency:tree -Dincludes=org.springframework
~~~

Bạn thường nhìn thấy `spring-context` và một số modules nền kéo theo. **Không khóa cứng danh sách/count transitives** vì graph có thể đổi theo patch và tính năng. Dùng:

~~~bash
mvn help:effective-pom
~~~

để xem model POM sau parent/dependency management/plugins. Khi nghi version conflict, đọc `dependency:tree` để xem module nào kéo tới dependency sai version. Trên Gradle:

~~~bash
./gradlew dependencies --configuration runtimeClasspath
./gradlew dependencyInsight --dependency spring-context --configuration runtimeClasspath
~~~

Tập lệnh `./gradlew` chỉ áp dụng nếu dự án có wrapper; trên Windows đổi sang `gradlew.bat`.

### Khi dự án cần nhiều Spring artifacts

Một cách kiểm soát version trong **dự án Framework không dùng Boot** là import `spring-framework-bom`:

~~~xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-framework-bom</artifactId>
            <version>7.0.9</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-context</artifactId>
    </dependency>
</dependencies>
~~~

**Đây là phương án thay thế**, không phải phần bắt buộc phải chèn thêm vào POM mục 5.1. BOM chỉ **quản lý phiên bản** cho dependency bạn khai báo; BOM **không** tự thêm mọi Spring module vào classpath.

Khi ứng dụng dùng **Spring Boot**, ưu tiên BOM/parent/plugin Boot để Spring Framework, web, data và thư viện tích hợp được đồng bộ. Không trộn một bản Framework 7 với những Spring modules 6.2 được ép version riêng rồi xem đó là một dependency set hợp lệ.

## 8. Chẩn đoán lỗi ngay khi thiết lập

| Triệu chứng | Điều cần kiểm tra | Hướng xử lý |
| --- | --- | --- |
| `package org.springframework.context... does not exist` | Thiếu `spring-context` trên compile classpath hoặc IDE chưa sync | Khai báo đúng dependency, reload Maven/Gradle, dùng build tool compile |
| `Could not find artifact org.springframework:...` | Artifact/version sai, mạng, mirror, proxy, offline | Soát GAV, Maven Central, `settings.xml` hoặc repository nội bộ |
| `release version 17 not supported` | Maven compiler chạy JDK cũ hơn 17 | Kiểm tra `mvn -version`, `JAVA_HOME`, toolchain |
| `UnsupportedClassVersionError` | Java runtime quá cũ so với class file | Đồng bộ runtime và bytecode/JDK |
| `NoClassDefFoundError` | Runtime classpath thiếu JAR hoặc dependency | Xem resolved graph và lệnh chạy |
| `NoSuchMethodError` | Có thể trộn versions hoặc binary mismatch | Kiểm tra dependency tree, BOM và exclusions |
| `NoSuchBeanDefinitionException` | Class hoặc bean chưa được đăng ký trong context | Xem `@Bean`, scan, type và context |
| `NoUniqueBeanDefinitionException` | Nhiều bean candidates cùng type | Xem `@Qualifier`, `@Primary`, bean names |
| IDE đỏ import nhưng Maven build tốt | IDE index/config module chưa reload | Refresh Maven/Gradle, xem source root/module |
| Console app thoát ngay | `main` kết thúc, context đã được đóng | Có thể là hành vi đúng; DI demo không cần HTTP server |

**Phân biệt rõ:** lỗi Maven download khác lỗi `javac` thiếu package; lỗi classpath runtime khác lỗi Spring không resolve được bean. Đừng xóa toàn bộ Maven cache theo thói quen; hãy xác định lỗi thuộc tầng nào trước khi đổi version hay cleanup.

Nếu chỉ cài `spring-core` mà import `ApplicationContext` vẫn lỗi, hãy chọn đúng **`spring-context`**, không phải sửa tên package Java một cách tùy ý.

### Tải JAR thủ công và JPMS

Có thể dùng `javac`/`java` với `-classpath` và mọi JAR cần thiết, nhưng phải tải **đúng transitives** và quản lý cách phân cách đường dẫn classpath (`;` trên Windows, `:` trên Linux/macOS). Điều này dễ thiếu JAR và khó nâng version; học Spring bằng Maven/Gradle an toàn hơn.

Ví dụ ở trên dùng **classpath thông thường**, không tạo `module-info.java`. JPMS/module path là chủ đề riêng; không tự chuyển các JAR và reflection/proxy của Spring sang module path rồi kỳ vọng hành vi giống hệt mà chưa kiểm tra metadata và module openness.

## 9. Quy trình đề xuất cho dự án thực tế

1. Chọn phiên bản Java được runtime/CI hỗ trợ, đồng bộ IDE/Maven/Gradle.
2. Chọn một release line Spring đang được hỗ trợ và một phương án quản lý dependency (Framework BOM hoặc Boot BOM tương ứng).
3. Thêm đúng artifact theo chức năng — Core Container trước hết là `spring-context`.
4. Tạo demo console nhỏ có `@Configuration`, `@Bean`, `ApplicationContext` và kiểm chứng stdout.
5. Xem dependency graph, kiểm tra có nhiều major versions Spring lẫn nhau không.
6. Thêm JUnit/tests, CI và upgrade notes khi đưa web/data/AOP/transaction vào dự án.
7. Dùng wrapper để tái hiện build trên máy khác; nâng patch bảo mật có test và rollback plan.

**Không nhất thiết tạo Spring Boot project** chỉ để thử `AnnotationConfigApplicationContext`. Nhưng nếu xây app web/data lớn, Boot có thể giảm công cấu hình. Hai đường đi đều sử dụng container của Spring Framework, không phải hai hệ DI độc lập.

## 10. Bài tập và câu hỏi tự kiểm tra

- **Đổi implementation:** thêm `VietnameseFormatter` trả `"Xin chao, " + name + "!"`, thay bean `formatter()`. Dự đoán `message=Xin chao, An!` mà không sửa `GreetingService`.
- **Xóa dependency trên bản sao:** tạm bỏ `spring-context` trong POM, chạy `mvn compile` và ghi lại lỗi `package org.springframework... does not exist`. Khôi phục POM ngay sau thử nghiệm.
- **Đổi version sai:** thử một version không tồn tại trên bản sao và quan sát Maven resolution failure, không nhầm với Java compiler error.
- **Thêm bean thứ hai:** tạo thêm `Formatter`, quan sát khả năng ambiguous injection và học cách dùng `@Qualifier`/`@Primary`.
- **Đổi scope:** đặt `GreetingService` là prototype, lấy bean hai lần, dự đoán `sameSingleton=false`; sửa tên nhãn thành `sameInstance` cho đúng ngữ nghĩa.
- **Kiểm JDK:** xem `java -version`, `javac -version`, `mvn -version`; giải thích tại sao JVM của build tool khác runtime sẽ gây lỗi.
- **Đọc dependency tree:** tìm artifact khai báo trực tiếp và module transitive, kiểm tra version Spring đang được resolve.
- **Thử Gradle:** chạy cùng class Java qua `application` plugin; so sánh ba dòng stdout với Maven.

Bạn đã nắm bài nếu trả lời được: `spring-context` khác `spring-core` ở đâu; artifact `org.springframework:spring-context:7.0.9` có ba thành phần gì; vì sao Java 17 phù hợp với Spring 7; khi nào nên dùng BOM; và cách chứng minh container đã inject được một dependency.

## 11. Tóm tắt và tài liệu tham khảo

**Ghi nhớ:** thiết lập Spring Core Container không chỉ là tải một JAR. Hãy chọn đúng **module `spring-context`**, một **dòng phiên bản tương thích với JDK và dependency khác**, dùng Maven/Gradle để resolve transitives, rồi kiểm chứng bằng chương trình tạo `ApplicationContext` thực sự.

Tài liệu chính thức:

- [Spring Framework Artifacts](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-Artifacts)
- [Spring Framework Reference](https://docs.spring.io/spring-framework/reference/)
- [Spring Framework 7.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes)
- [Spring IoC Container](https://docs.spring.io/spring-framework/reference/core/beans.html)
- [Maven Dependency Mechanism](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html)
- [Gradle Dependency Management](https://docs.gradle.org/current/userguide/dependency_management.html)

**Kết luận:** Nếu `mvn -q clean compile exec:java` biên dịch được và in đúng ba dòng theo mục 5.3, bạn đã có môi trường Core Container hoạt động, đủ cơ sở để học tiếp đăng ký bean, DI, scope và lifecycle.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="first-application-002">Tạo lớp nghiệp vụ và hợp đồng cho đối tượng cộng tác</a>

<details>
<summary>Xem chi tiết</summary>

# Tạo lớp nghiệp vụ và hợp đồng cho đối tượng cộng tác — ứng dụng Spring đầu tiên

## 1. Mục tiêu và bức tranh ứng dụng

Một ứng dụng có ích hiếm khi chỉ có một `main` chứa tất cả câu lệnh. Ta thường cần một **lớp nghiệp vụ** điều phối một ca sử dụng (*use case*), và các **đối tượng cộng tác** (*collaborators*) cung cấp thao tác lưu trữ, gửi thông báo hoặc gọi hệ thống bên ngoài.

Trong ví dụ đặt hàng tối giản:

- Người dùng gửi yêu cầu có mã đơn, mã hàng, số lượng.
- `OrderService` kiểm tra/điều phối nghiệp vụ tiếp nhận đơn.
- `OrderStore` là **hợp đồng** để lưu đơn, không buộc service biết dữ liệu nằm trong bộ nhớ hay database.
- `Notifier` là **hợp đồng** để thông báo khi đơn được tiếp nhận, không buộc service biết gửi console, email hay message queue.
- Một điểm khởi động tạo các triển khai thật, sau đó truyền chúng vào `OrderService`. Khi dùng Spring, `ApplicationContext` cùng `@Configuration`/`@Bean` có thể đảm trách bước lắp ráp này.

Bài học chính: **mô tả các lớp và hợp đồng trước; để vị trí lắp ráp quyết định triển khai sau**. Có thể thực hành hoàn toàn bằng Java 17 rồi mới thay bước lắp ráp bằng Spring Core Container.

~~~text
                request OrderRequest
                        |
                        v
                 +--------------+
                 | OrderService |        lớp nghiệp vụ / use case
                 +--------------+
                   |          |
          uses     |          |     uses
                   v          v
             OrderStore     Notifier      hợp đồng cộng tác
                  ^           ^
                  |           |
         InMemoryOrderStore  ConsoleNotifier
                       triển khai cụ thể
                              ^
                              |
                   main hoặc Spring @Configuration
                      chọn, tạo, nối đối tượng
~~~

Hợp đồng giúp giới hạn điều mà bên sử dụng **cần biết**, không phải một cơ chế tự động chặn mọi phụ thuộc. Giao diện vẫn có thể thiết kế tệ, quá rộng hoặc buộc domain biết chi tiết hạ tầng nếu đặt sai trách nhiệm.

## 2. Xác định vai trò trước khi viết code

| Thành phần | Vai trò | Nên biết | Không nên tự ôm |
| --- | --- | --- | --- |
| `OrderRequest` | Dữ liệu đầu vào hợp lệ, bất biến | Mã đơn, mã hàng, số lượng | Cách kết nối database hay gửi email |
| `OrderService` | Thực hiện hành vi tiếp nhận đơn | Quy tắc xử lý và các **hợp đồng** cần gọi | URL SMTP, tên driver, SQL tạo kết nối |
| `OrderStore` | Hợp đồng lưu đơn | Hàm lưu và ý nghĩa lỗi trùng mã | Định dạng bảng SQL cụ thể |
| `Notifier` | Hợp đồng thông báo | Hàm nhận thông tin cần gửi | Địa chỉ server cụ thể |
| `InMemoryOrderStore` | Triển khai demo lưu trong RAM | Cấu trúc Map và kiểm tra trùng mã | Quyết định nghiệp vụ của service |
| `ConsoleNotifier` | Triển khai demo in thông báo | Cách viết stdout | Điều kiện chấp nhận đơn |
| `main` / `AppConfig` | Composition root | Triển khai nào được chọn, object graph | Logic đặt hàng của `OrderService` |

**Lớp nghiệp vụ khác lớp dữ liệu:** một `record` chứa dữ liệu không tự biến thành service. `OrderService` không phải DTO vì nó có hành vi; `OrderStore` là contract chứ không phải service quản lý HTTP.

**Collaborator không nhất thiết là interface.** Trong Java/Spring vẫn có thể inject một class cụ thể, đặc biệt khi đó là dependency ổn định và không cần nhiều triển khai. Ta dùng interface ở đây vì muốn thay `ConsoleNotifier` bằng fake trong kiểm thử, hoặc đổi phương án lưu đơn mà không sửa quy tắc trong `OrderService`.

## 3. Thiết kế hợp đồng có nghĩa

Một hợp đồng tốt tập trung vào việc lớp nghiệp vụ **muốn thực hiện**, thay vì mô tả cách một công nghệ cụ thể vận hành.

~~~java
public interface OrderStore {
    void save(OrderRequest order);
}

public interface Notifier {
    void notifyPlaced(String orderId);
}
~~~

Đây là **trích đoạn minh họa**, chưa phải file Java độc lập: `OrderRequest` được định nghĩa ở phần chương trình đầy đủ bên dưới.

Hai phương thức có ý nghĩa rõ:

- `save(order)`: lưu một đơn; nếu có mã trùng trong demo sẽ báo lỗi, không âm thầm ghi đè.
- `notifyPlaced(id)`: báo rằng đơn đã được tiếp nhận; client không cần biết cách truyền thông điệp.

Tránh thiết kế hợp đồng quá mơ hồ kiểu `void doSomething(Object input)`, hoặc quá gắn hạ tầng như `void executeSql(String sql)` nếu dịch vụ chỉ cần `save(order)`. Mức trừu tượng nên phù hợp nhu cầu thật, không tạo interface một cách máy móc cho tất cả class.

**Định nghĩa semantics và lỗi:** giao diện phải được các triển khai hiểu nhất quán. Ví dụ bên gọi cần biết `save` có tạo mới, cập nhật hay cấm trùng; việc đổi triển khai không được tùy tiện làm mất các quy tắc đó. Nếu cần trả trạng thái hoặc xử lý lỗi đặc biệt, có thể mở rộng contract bằng kiểu trả về và ngoại lệ chuyên biệt khi thiết kế hệ thống thật.

### Ranh giới kiểm tra dữ liệu

Chọn hai cấp kiểm tra:

1. `OrderRequest` bảo đảm trạng thái không vô nghĩa (`id`/`sku` không trống, `quantity` dương).
2. `OrderService` điều phối bước lưu và thông báo, không lặp lại mọi kiểm tra đã được constructor `OrderRequest` thực hiện.

Ví dụ này **chưa có thanh toán, tồn kho, database transaction**. Tách phạm vi như vậy giúp người học nhìn rõ DI và thiết kế collaborator trước khi học hạ tầng phức tạp hơn.

## 4. Chương trình Java 17 độc lập, chưa cần Spring

Tạo thư mục dự án như sau. Cùng file `PlainJavaDemo.java` sẽ được dùng lại ở ví dụ Spring phần 6; không cần sao chép domain sang project khác.

~~~text
first-business-app/
├── pom.xml                         # tạo ở mục Spring; Java thuần chưa cần
└── src/
    └── main/
        └── java/
            └── demo/
                ├── PlainJavaDemo.java
                └── SpringBusinessApp.java  # tạo ở mục Spring
~~~

Tệp đầu tiên: **`src/main/java/demo/PlainJavaDemo.java`**.

~~~java
package demo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PlainJavaDemo {
    private PlainJavaDemo() {
    }

    // Value object bất biến, tự kiểm tra điều kiện hợp lệ.
    public record OrderRequest(String id, String sku, int quantity) {
        public OrderRequest {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("id must not be blank");
            }
            if (sku == null || sku.isBlank()) {
                throw new IllegalArgumentException("sku must not be blank");
            }
            if (quantity <= 0) {
                throw new IllegalArgumentException("quantity must be positive");
            }
        }
    }

    // Hợp đồng của các cộng tác viên.
    public interface OrderStore {
        void save(OrderRequest order);
    }

    public interface Notifier {
        void notifyPlaced(String orderId);
    }

    // Triển khai lưu trong RAM để ví dụ không cần database.
    public static final class InMemoryOrderStore implements OrderStore {
        private final Map<String, OrderRequest> orders = new HashMap<>();

        @Override
        public void save(OrderRequest order) {
            Objects.requireNonNull(order, "order");
            if (orders.putIfAbsent(order.id(), order) != null) {
                throw new IllegalStateException("duplicate order: " + order.id());
            }
        }

        public boolean contains(String id) {
            return orders.containsKey(id);
        }
    }

    // Triển khai gửi thông báo bằng console.
    public static final class ConsoleNotifier implements Notifier {
        @Override
        public void notifyPlaced(String orderId) {
            System.out.println("NOTICE " + orderId);
        }
    }

    // Lớp nghiệp vụ: KHÔNG tự new InMemoryOrderStore/ConsoleNotifier.
    public static final class OrderService {
        private final OrderStore store;
        private final Notifier notifier;

        public OrderService(OrderStore store, Notifier notifier) {
            this.store = Objects.requireNonNull(store, "store");
            this.notifier = Objects.requireNonNull(notifier, "notifier");
        }

        public String place(OrderRequest order) {
            Objects.requireNonNull(order, "order");
            store.save(order);
            notifier.notifyPlaced(order.id());
            return "ACCEPTED " + order.id();
        }
    }

    public static void main(String[] args) {
        // Composition root Java thuần: chọn và lắp ráp dependency.
        InMemoryOrderStore store = new InMemoryOrderStore();
        Notifier notifier = new ConsoleNotifier();
        OrderService service = new OrderService(store, notifier);

        String result =
                service.place(new OrderRequest("O-101", "BOOK", 2));
        System.out.println(result);
        System.out.println("FOUND " + store.contains("O-101"));

        // Fake dùng trong unit test: không gửi thông báo thật.
        List<String> calls = new ArrayList<>();
        Notifier fake = id -> calls.add(id);
        OrderService tested = new OrderService(new InMemoryOrderStore(), fake);

        String testedResult =
                tested.place(new OrderRequest("T-9", "PEN", 1));
        System.out.println(testedResult);
        System.out.println("FAKE_CALLS " + calls);

        if (!calls.equals(List.of("T-9"))) {
            throw new AssertionError("Notifier contract was not called once");
        }
        System.out.println("TEST PASS");
    }
}
~~~

### Biên dịch và chạy Java 17+

Mở terminal tại thư mục **`first-business-app`**, dùng JDK 17 trở lên. PowerShell/CMD/Linux/macOS đều chạy được các lệnh sau (dấu `/` trong đường dẫn được Java tooling hỗ trợ):

~~~bash
javac --release 17 -encoding UTF-8 -d out src/main/java/demo/PlainJavaDemo.java
java -cp out demo.PlainJavaDemo
~~~

**Stdout chính xác từ chương trình `PlainJavaDemo`:**

~~~text
NOTICE O-101
ACCEPTED O-101
FOUND true
ACCEPTED T-9
FAKE_CALLS [T-9]
TEST PASS
~~~

Phân tích theo thứ tự:

1. `main` tạo `InMemoryOrderStore`, `ConsoleNotifier` rồi truyền vào constructor `OrderService`.
2. `OrderService.place` gọi `store.save`, sau đó `notifier.notifyPlaced`; thông báo thật in `NOTICE O-101`.
3. `main` in giá trị trả về `ACCEPTED O-101` và xác nhận đơn tồn tại trong store.
4. Ở phép thử, `fake` là lambda thực hiện hợp đồng `Notifier` bằng cách ghi lại mã đơn vào list. Do đó **không có dòng `NOTICE T-9`**.
5. Test so sánh các lời gọi với `List.of("T-9")`, chứng minh service đã gửi chính xác một thông báo cho mã đơn mong đợi.

**Vì sao dùng `record`?** `OrderRequest` là dữ liệu đầu vào có các component truy cập qua `id()`, `sku()`, `quantity()`. Record được hỗ trợ ổn định ở Java 17; *compact constructor* `public OrderRequest { ... }` kiểm tra dữ liệu đầu vào và không phải tự gán lại từng field khi điều kiện hợp lệ.

**Lưu ý về thứ tự side effect:** demo lưu rồi thông báo. Nếu thông báo lỗi sau khi lưu, thao tác lưu đã xảy ra; Java thuần không tự rollback `Map`. Ứng dụng thật cần chính sách rõ như retry, idempotency, transaction/outbox khi liên quan tới nhiều hệ thống. Đó là chủ đề nâng cao, không nên ngầm tuyên bố ví dụ này “atomic”.

## 5. Từng quyết định thiết kế đang giải quyết vấn đề gì?

### 5.1. Lớp nghiệp vụ chỉ biết contract

~~~java
public OrderService(OrderStore store, Notifier notifier) {
    this.store = Objects.requireNonNull(store, "store");
    this.notifier = Objects.requireNonNull(notifier, "notifier");
}
~~~

Đây là trích đoạn đã nằm trong `PlainJavaDemo.java`. Constructor thể hiện **dependency bắt buộc**. Dùng `final` bảo đảm reference không được gán lại sau khi tạo object, không mặc nhiên biến đối tượng phụ thuộc thành bất biến hay thread-safe.

Lớp nghiệp vụ **không phụ thuộc vào tên các implementation**. Khi muốn in ra file thay vì console hoặc lưu vào database thay vì Map, chính điểm lắp ráp sẽ thay triển khai tương ứng (với điều kiện các implementation giữ contract).

### 5.2. Phân biệt `uses` với `creates`

Hai cách viết:

~~~java
// Ghép chặt: dịch vụ tự chọn và tạo cộng tác viên.
final class BadOrderService {
    private final ConsoleNotifier notifier = new ConsoleNotifier();
}
~~~

~~~java
// Tiêm dependency: dịch vụ chỉ khai báo thứ nó cần.
final class BetterOrderService {
    private final Notifier notifier;

    BetterOrderService(Notifier notifier) {
        this.notifier = Objects.requireNonNull(notifier);
    }
}
~~~

Hai khối **minh họa thiết kế**, không runnable độc lập vì `ConsoleNotifier` và `Notifier` ở phần này dùng lại từ ví dụ chính. Điểm cần nhớ không phải cấm `new`, mà là **đưa quyền chọn và dựng collaborator đến nơi có trách nhiệm lắp ráp**.

Một object dữ liệu ngắn hạn như `new ArrayList<>()` trong nội bộ method không nhất thiết phải thành Spring bean. Trái lại, một HTTP client, mail sender hoặc repository cần cấu hình/lifecycle thường là ứng viên tốt để truyền từ ngoài vào.

### 5.3. Hợp đồng không có nghĩa mọi thay đổi đều miễn phí

Thay implementation là thuận lợi khi giữ đúng hành vi đã thỏa thuận. Nếu nghiệp vụ đổi từ “chỉ lưu đơn” sang “trừ tồn kho, thu tiền, có kiểm tra rủi ro”, chính `OrderService` và contract có thể cần thay đổi. Tránh quảng cáo interface như cách không bao giờ phải sửa business code.

Nếu một interface phình ra với hàng chục method không liên quan, hãy chia theo năng lực consumer cần dùng (Interface Segregation). Nếu chỉ có một triển khai ổn định và không cần abstraction, constructor injection của concrete class vẫn có thể hợp lý.

### 5.4. Nên tổ chức file thế nào khi ứng dụng lớn hơn?

Trong dự án thật, nên tách `OrderRequest`, `OrderService`, `OrderStore`, `Notifier` và implementation thành các file/package rõ ràng. Ví dụ:

~~~text
demo.order/
    OrderRequest.java
    OrderService.java
    OrderStore.java
    Notifier.java
demo.adapters/
    InMemoryOrderStore.java
    ConsoleNotifier.java
demo.config/
    AppConfig.java
~~~

Đây là **mẫu tổ chức package**, không phải cấu trúc cần tạo để chạy demo một file. Các package có thể được đặt tên khác tùy module boundaries; đừng để domain phụ thuộc vào adapter chỉ vì chúng nằm cùng thư mục trong bài thực hành.

## 6. Thay bước lắp ráp bằng Spring Core Container

Vẫn dùng nguyên file `PlainJavaDemo.java` ở mục 4. Chỉ thêm **`pom.xml`** và **`SpringBusinessApp.java`**. Điều này cho thấy ứng dụng **không cần viết lại service để “chuyển sang Spring”**: ta chỉ thay nơi tạo và đưa các dependency vào.

### 6.1. Maven dependency

Dùng cùng mốc Spring Framework 7.0.9/Java 17 như bài chuẩn bị thư viện. Ví dụ là ứng dụng console với `spring-context`, **không sử dụng Spring Boot, web server hay database**.

Tạo `pom.xml` tại thư mục `first-business-app`:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>edu.example</groupId>
    <artifactId>first-business-app</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>7.0.9</version>
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
                    <mainClass>demo.SpringBusinessApp</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 6.2. Cấu hình bean và chương trình Spring

Tạo `src/main/java/demo/SpringBusinessApp.java`:

~~~java
package demo;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public final class SpringBusinessApp {
    private SpringBusinessApp() {
    }

    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {
        @Bean
        public PlainJavaDemo.InMemoryOrderStore orderStore() {
            return new PlainJavaDemo.InMemoryOrderStore();
        }

        @Bean
        public PlainJavaDemo.Notifier notifier() {
            return new PlainJavaDemo.ConsoleNotifier();
        }

        @Bean
        public PlainJavaDemo.OrderService orderService(
                PlainJavaDemo.OrderStore store,
                PlainJavaDemo.Notifier notifier) {
            return new PlainJavaDemo.OrderService(store, notifier);
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {
            PlainJavaDemo.OrderService service =
                    context.getBean(PlainJavaDemo.OrderService.class);

            String result = service.place(
                    new PlainJavaDemo.OrderRequest("S-202", "NOTEBOOK", 3));

            System.out.println(result);
            System.out.println("FOUND "
                    + context.getBean(
                            PlainJavaDemo.InMemoryOrderStore.class
                      ).contains("S-202"));
        }
    }
}
~~~

Từ thư mục `first-business-app` (có `pom.xml`), chạy:

~~~bash
mvn -version
mvn -q compile exec:java
~~~

**Các dòng stdout do `SpringBusinessApp` in, theo thứ tự:**

~~~text
NOTICE S-202
ACCEPTED S-202
FOUND true
~~~

`mvn -version` và Maven/plugin có thể in các dòng riêng (phiên bản, log hoặc tiến trình tải dependency). Khối stdout trên chỉ là **ba dòng do logic ví dụ tạo ra**. Lần đầu cần Maven có khả năng tải dependency nếu cache chưa có.

### 6.3. Spring đã làm gì thay `main` Java thuần?

1. `AnnotationConfigApplicationContext(AppConfig.class)` đọc lớp cấu hình và khởi tạo context.
2. `@Bean orderStore()` tạo `InMemoryOrderStore`; `@Bean notifier()` tạo `ConsoleNotifier`. Chúng trở thành bean được quản lý bởi container.
3. `@Bean orderService(OrderStore store, Notifier notifier)` khai báo hai dependency bằng kiểu hợp đồng. Spring giải quyết các bean phù hợp và truyền chúng vào tham số phương thức factory.
4. `new PlainJavaDemo.OrderService(store, notifier)` vẫn hiện diện **trong configuration**, nhưng `OrderService` không tự `new` những collaborator đó.
5. `context.getBean(OrderService.class)` lấy service đã nối đầy đủ; hành vi `place` và dữ liệu `OrderRequest` giữ nguyên.
6. `try-with-resources` đóng context. Đối với bean thuộc quản lý Spring, các callback hủy phù hợp được xử lý theo vòng đời của context.

**Không cần `@Autowired` ở các tham số `@Bean`**: Spring giải quyết dependency của factory method. Cũng không cần `@ComponentScan` vì tất cả bean đã được đăng ký qua `@Bean` trong `AppConfig`.

**Không cần đặt `@Service` lên `PlainJavaDemo.OrderService`**: bản thân class có thể là Java class thông thường; container quản lý instance mà `@Bean` trả về. Annotation Spring nên dùng vì nó phục vụ một cơ chế cụ thể, không phải để làm cho domain “trông giống Spring”.

## 7. Ranh giới giữa bản Java và bản Spring

| Tiêu chí | Java thuần | Spring Core Container |
| --- | --- | --- |
| Ai gọi `new` collaborator? | `main`, factory hoặc composition root | `@Bean` factory/configuration hoặc cơ chế tạo bean |
| Ai truyền `OrderStore` và `Notifier` vào service? | Mã lắp ráp thủ công | Container giải quyết tham số `@Bean` |
| Service có phải import Spring? | Không | **Không**, nếu dùng domain độc lập như ví dụ |
| Có thể thay fake khi test? | Có, truyền qua constructor | Có, và có thể test service **không cần** context |
| Đối tượng có thành Spring bean? | Không, trừ khi tự đăng ký vào container | Có với instance tạo/đăng ký qua `@Bean` |
| Lifecycle chính | Người tạo/chủ sở hữu tự quản | Container phối hợp lifecycle cho bean thuộc quản lý |
| Có tự động REST endpoint/transaction? | Không | **Không**; cần hạ tầng riêng nếu muốn |

**DI không đồng nghĩa Spring**; constructor injection đã xuất hiện trong ví dụ Java thuần. Spring giúp cấu hình và quản lý object graph khi hệ thống lớn lên. Ngược lại, dùng Spring không tự làm thiết kế hợp đồng trở nên đúng: nếu đặt logic nghiệp vụ vào `@Configuration` hay dùng `ApplicationContext.getBean(...)` khắp nơi, kiến trúc vẫn có thể khó kiểm thử và khó bảo trì.

### Tránh nhầm singleton

Mặc định, `@Bean` thường có scope `singleton` trong *một* container. Điều này nói về một instance của bean theo định nghĩa trong context, **không đồng nghĩa an toàn đa luồng**. `InMemoryOrderStore` ở đây dùng `HashMap` để demo; **không thích hợp làm kho lưu trữ production đa luồng** nếu không có cơ chế đồng bộ/chọn storage phù hợp. Đừng dùng nó như database thật.

## 8. Kiểm thử contract và lớp nghiệp vụ

### 8.1. Unit test không cần Spring

Với dependency qua constructor, một fake có thể thay thế `Notifier`. Mục 4 đã minh họa `List<String> calls` để xác nhận service phát ra đúng mã đơn. Ta cũng có thể kiểm tra rằng đối số không hợp lệ bị từ chối trước khi truy cập store.

Một phép thử bổ sung **độc lập về ý tưởng**, có thể chèn vào `main` Java thuần (không thêm vào chương trình chính nếu muốn giữ stdout mục 4):

~~~java
try {
    new PlainJavaDemo.OrderRequest("BAD", "BOOK", 0);
    throw new AssertionError("Expected validation failure");
} catch (IllegalArgumentException expected) {
    System.out.println("VALIDATION PASS");
}
~~~

Nếu đặt khối trên vào `main` sau các ví dụ, sẽ **in thêm** `VALIDATION PASS`. Không coi nó là một phần stdout đã công bố ở mục 4.

Có thể thử tiếp:

- `new OrderService(null, notifier)` phải ném `NullPointerException` do `requireNonNull`.
- Gọi `save` hai lần với cùng `id` trên một `InMemoryOrderStore` phải từ chối lần thứ hai.
- Khi `Notifier` fake ném exception, theo thiết kế demo, store đã lưu đơn. Test nên làm rõ giới hạn này thay vì giả định có rollback.
- Thay implementation `OrderStore` bằng fake ghi nhận input, kiểm tra service chuyển đúng `OrderRequest`.

### 8.2. Integration test với Spring Context

Unit test của `OrderService` không nhất thiết phải khởi động Spring. Nhưng để kiểm tra *wiring*, có thể dựng context từ `AppConfig` trong một test, lấy `OrderService` bằng `getBean` và xác minh dependency/cách gọi.

Điều cần kiểm tra ở integration test:

- Có đúng một ứng viên `OrderStore` và `Notifier` phù hợp để inject.
- Cấu hình context khởi động thành công.
- Bean của `OrderService` lấy được và không có dependency bị thiếu.
- Nếu thêm nhiều implementation cùng interface, cơ chế lựa chọn (`@Primary`, `@Qualifier`, tên...) rõ ràng.
- Không test “chính xác có một bean trong toàn JVM”: singleton là theo container.

Khi test wiring với data source/email thật, cần tách môi trường test để tránh phát sinh tác dụng phụ ngoài ý muốn.

## 9. Hướng phát triển từ ví dụ đầu tiên

Khi triển khai thành ứng dụng có ích hơn, có thể phát triển từng phần:

1. **Domain model:** thêm trạng thái đơn, giá và ràng buộc có ý nghĩa. Chọn kiểu số tiền thích hợp; tránh dùng `double` cho tính toán tiền yêu cầu chính xác.
2. **Repository:** thay `InMemoryOrderStore` bằng triển khai database, giữ đúng hợp đồng lưu và lỗi trùng mã.
3. **Notifier:** thêm email/queue, chính sách retry và idempotency thích hợp; tuyệt đối không hard-code secret vào business class.
4. **Service:** tách các quy tắc tồn kho, thanh toán, xét duyệt nếu use case yêu cầu; không dồn mọi hành vi vào một “God Service”.
5. **Cấu hình:** dồn việc chọn triển khai, tạo client và quản lý tài nguyên về adapter/configuration.
6. **Transaction:** nếu cần tính nhất quán nhiều bước lưu dữ liệu, cấu hình transaction manager và ranh giới giao dịch. `@Bean` đơn thuần không cung cấp database transaction.
7. **Web API:** khi cần HTTP, thêm controller và Spring Web MVC hoặc WebFlux. `spring-context` một mình không tự mở cổng HTTP.
8. **Quan sát lỗi:** đưa ra exception/log rõ ràng; xác định chính sách retry nếu gửi thông báo thất bại.
9. **Kiểm thử:** unit test service bằng fake, integration test wiring và persistence, acceptance test cho luồng end-to-end.

Lưu ý thứ tự thiết kế: **hợp đồng phản ánh yêu cầu của lớp sử dụng**, sau đó lựa chọn công nghệ adapter. Không nên để tên thư viện/hạ tầng chi phối toàn bộ API nghiệp vụ.

## 10. Lỗi phổ biến và cách nhận diện

| Triệu chứng | Nguyên nhân / hướng xử lý |
| --- | --- |
| Service vừa xử lý đơn vừa tự `new JdbcConnection` | Trộn nghiệp vụ và construction detail; đưa collaborator ra constructor |
| Trường khai báo `OrderStore` nhưng khởi tạo `new SqlOrderStore()` ngay trong service | Vẫn khóa cách tạo triển khai cụ thể; interface ở biến chưa đủ |
| Constructor có 10–15 dependency không liên quan | Xem lại trách nhiệm service; cân nhắc tách use case/aggregator thay vì che bằng injection |
| Service gọi `ApplicationContext.getBean(...)` mọi nơi | Service Locator làm dependency ẩn; ưu tiên constructor injection |
| `@Bean orderService` không tìm thấy `OrderStore` | Chưa đăng ký bean, sai type hoặc profile; kiểm tra configuration |
| Có 2 bean `Notifier` và injection mơ hồ | Chọn triển khai bằng `@Qualifier`/`@Primary` hoặc thiết kế cấu hình rõ |
| Tự `new OrderService(...)` ngoài context rồi chờ bean lifecycle | Object ngoài container không tự được quản lý như bean |
| Dùng `HashMap` store của demo để phục vụ nhiều request đồng thời | Mutable state không thread-safe; cần storage/synchronization phù hợp |
| Fake test chỉ kiểm tra không ném lỗi, không kiểm tra lời gọi | Nên assert mã đơn được gửi và số lần gọi |
| Gọi `Notifier` fail rồi tin rằng store tự rollback | Không có transaction/compensation; thiết kế policy nhất quán rõ ràng |
| Bỏ qua kiểm tra `id`/`quantity` | Dữ liệu invalid lan sâu vào collaborator; đặt invariant ở đúng đối tượng |
| Viết interface chỉ để “đúng mẫu” dù không có mục đích | Tránh abstractions dư thừa; thiết kế hướng tới khả năng thay thế/ranh giới cụ thể |

## 11. Bài tập tự luyện có đáp án

**Bài 1 — xác định phụ thuộc bị khóa.** Vì sao đoạn này khó test hơn?

~~~java
class OrderService {
    private final SmtpNotifier notifier = new SmtpNotifier();
}
~~~

Đây là mã minh họa (thiếu class `SmtpNotifier`). **Đáp án:** service tự chọn triển khai SMTP và tự khởi tạo. Nếu muốn thay bằng fake để test, phải sửa mã hoặc tạo đường vòng; constructor injection giải quyết bằng cách nhận contract `Notifier`.

**Bài 2 — thêm lựa chọn triển khai.** Giả sử có `RecordingNotifier implements Notifier`. Có cần sửa `OrderService.place` để dùng fake không?

**Đáp án:** Không, nếu fake tuân thủ contract. Chỉ truyền `RecordingNotifier` khi tạo service hoặc thay bean `Notifier` trong cấu hình dùng cho test.

**Bài 3 — trách nhiệm của `@Bean`.** Vì sao code `return new PlainJavaDemo.InMemoryOrderStore();` trong `@Bean` không phải vi phạm quy tắc tránh `new` ở lớp nghiệp vụ?

**Đáp án:** `@Configuration` có trách nhiệm **chọn và tạo dependency**. `OrderService` không biết lớp concrete và không phải nơi tạo adapter.

**Bài 4 — phân biệt contract và data.** `OrderRequest` có nên được tạo làm singleton Spring bean để tái sử dụng ở mọi đơn không?

**Đáp án:** Không. `OrderRequest` chứa dữ liệu từng lần gọi. Dùng record immutable mới cho từng yêu cầu; biến nó thành singleton bean thường tạo ràng buộc vòng đời sai.

**Bài 5 — quy tắc xử lý lỗi.** Nếu đơn lưu thành công nhưng gửi thông báo thất bại, trong ví dụ hiện tại có rollback không?

**Đáp án:** Không. `save` đã chạy trước `notifyPlaced`, không có transaction hoặc cơ chế bù trừ. Cần policy rõ khi muốn xử lý trường hợp này trong ứng dụng thật.

**Bài 6 — đổi persistence.** Nếu chuyển từ `InMemoryOrderStore` sang `JdbcOrderStore`, phần nào sẽ thay?

**Đáp án:** Viết adapter mới tuân thủ `OrderStore`, cấu hình bean/factory và bổ sung datasource/driver/test thích hợp. `OrderService` không cần đổi nếu contract và quy tắc nghiệp vụ giữ nguyên.

## 12. Checklist trước khi hoàn thành ứng dụng đầu tiên

- [ ] Xác định một ca sử dụng có hành vi rõ (ví dụ tiếp nhận đơn).
- [ ] Đặt lớp nghiệp vụ theo hành vi: `OrderService.place`.
- [ ] Xác định dữ liệu đầu vào và invariant: `OrderRequest`.
- [ ] Chỉ tạo contract collaborator thực sự cần: `OrderStore`, `Notifier`.
- [ ] Cho mỗi implementation thực hiện một trách nhiệm chính, không “nhét” nghiệp vụ vào adapter.
- [ ] Khai báo dependency bắt buộc trong constructor, kiểm tra không null.
- [ ] Lắp ráp bằng `main` Java thuần trước, kiểm tra đầu ra và lỗi cơ bản.
- [ ] Tạo fake để test service không cần I/O thật.
- [ ] Khi thêm Spring, dùng `@Configuration`/`@Bean` để lắp object graph, không sửa business rules.
- [ ] Xác nhận `mvn compile exec:java` chạy được và phân biệt stdout chương trình với log Maven.
- [ ] Rà trường hợp duplicate id, input invalid, collaborator exception và side effect không atomic.
- [ ] Không coi IoC Container đồng nghĩa với database transaction, HTTP server hoặc unit test framework.

**Kết luận:** Khi xây dựng ứng dụng Spring đầu tiên, hãy tách **lớp nghiệp vụ** khỏi **hợp đồng của đối tượng cộng tác**. `OrderService` biểu đạt hành vi tiếp nhận đơn; `OrderStore` và `Notifier` biểu đạt năng lực mà service cần. Java 17 đã hỗ trợ constructor injection và fake test. Spring Core Container giúp tạo/nối các bean theo `@Configuration`/`@Bean` mà không buộc domain phụ thuộc trực tiếp vào Spring — đây là nền tảng để mở rộng ứng dụng mà vẫn kiểm soát được trách nhiệm từng lớp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="first-application-003">Khai báo cấu hình tối thiểu bằng @Configuration và @Bean</a>

<details>
<summary>Xem chi tiết</summary>

# Khai báo cấu hình tối thiểu bằng @Configuration và @Bean trong Spring Framework

## 1. Kết quả học tập

Ứng dụng Spring Framework đầu tiên có thể chỉ cần **JDK 17+, dependency `spring-context`, một lớp `@Configuration`, vài factory method `@Bean` và một phương thức `main`**. Không cần Spring Boot, XML, server HTTP hoặc cơ sở dữ liệu để quan sát container quản lý bean.

Kết thúc bài này, bạn có thể:

- Tạo project Maven với `spring-context` và biên dịch Java 17.
- Giải thích `@Configuration` đăng ký nguồn cấu hình; `@Bean` mô tả factory method cung cấp object bean cho container.
- Khai báo hai bean hợp tác với nhau, inject dependency qua tham số của phương thức `@Bean`, không để lớp nghiệp vụ gọi container.
- Khởi tạo `AnnotationConfigApplicationContext`, xác nhận tên bean, lấy bean theo kiểu, kiểm tra wiring và scope mặc định.
- Phân biệt gọi phương thức Java thông thường với Spring gọi factory method, nhất là với `proxyBeanMethods=false`.
- Đóng context an toàn và đối chiếu lệnh chạy/đầu ra Java 17 chính xác.
- Chẩn đoán thiếu bean, nhiều ứng viên, tên bean sai, cấu hình không được nạp, classpath thiếu và cách đăng ký trùng.

Phạm vi bài là **JavaConfig tối thiểu**. Những cơ chế component scanning, property injection, custom scope và Spring Boot auto-configuration chỉ nhắc tới khi giúp bạn không nhầm khái niệm.

## 2. Trước Spring: lớp sử dụng và lớp cung cấp dependency

Giả sử chương trình cần chào người dùng. Ta có một hợp đồng `GreetingTemplate` và service dùng hợp đồng đó:

~~~java
interface GreetingTemplate {
    String render(String name);
}

final class VietnameseTemplate implements GreetingTemplate {
    @Override
    public String render(String name) {
        return "Xin chao, " + name + "!";
    }
}

final class GreetingService {
    private final GreetingTemplate template;

    GreetingService(GreetingTemplate template) {
        this.template = java.util.Objects.requireNonNull(template);
    }

    String greet(String name) {
        return template.render(name.strip());
    }
}
~~~

Đây là **đoạn code minh họa**, chưa phải file chạy độc lập (thiếu lớp `main`). Nếu tự lắp ghép bằng Java thuần:

~~~java
GreetingTemplate template = new VietnameseTemplate();
GreetingService service = new GreetingService(template);
System.out.println(service.greet(" Lan ")); // Xin chao, Lan!
~~~

Đó đã là **constructor dependency injection**: service nhận collaborator được chọn từ bên ngoài, thay vì tự gọi `new VietnameseTemplate()` trong class nghiệp vụ. Spring giúp **lắp ghép đối tượng theo bean definitions**, không tạo ra một ngôn ngữ Java mới và cũng không cấm `new`: bên trong một factory method `@Bean`, `new` là cách tự nhiên để cung cấp object.

Một lớp service biết `GreetingTemplate`, không cần biết `ApplicationContext` hay Maven. Nếu đổi sang `EnglishTemplate` trong cấu hình, mã service không phải đổi miễn hợp đồng vẫn được đáp ứng.

## 3. Cấu hình tối thiểu gồm những gì?

Một cấu hình Java ngắn:

~~~java
@Configuration(proxyBeanMethods = false)
class AppConfig {
    @Bean
    GreetingTemplate greetingTemplate() {
        return new VietnameseTemplate();
    }

    @Bean
    GreetingService greetingService(GreetingTemplate template) {
        return new GreetingService(template);
    }
}
~~~

Đây là **minh họa**, thiếu import và các định nghĩa lớp liên quan. Ví dụ hoàn chỉnh ở mục 5.

| Cú pháp | Nghĩa trong bài này |
| --- | --- |
| `@Configuration` | Lớp chứa cấu hình để Spring xử lý thành bean definitions |
| `@Bean` | Factory method tạo/trả về object bean được container quản lý |
| `greetingTemplate()` | Tên bean mặc định thường là `greetingTemplate` |
| `greetingService(GreetingTemplate template)` | Tham số factory method chỉ ra dependency cần resolve |
| `new GreetingService(template)` | Constructor được gọi bởi factory method để lắp ghép dependency |
| `proxyBeanMethods=false` | Không intercept các lời gọi Java trực tiếp giữa các factory methods `@Bean` |

**Điều quan trọng:** factory method `greetingService(...)` không tự gọi `greetingTemplate()`. Spring thấy tham số cần một `GreetingTemplate` rồi cung cấp bean phù hợp. Từ đó service nhận đúng object do container quản lý.

~~~mermaid
flowchart LR
    C["ApplicationContext"] --> R["Bean: greetingTemplate"]
    C --> S["Bean: greetingService"]
    S -->|"constructor dependency"| R
~~~

Hướng cạnh `greetingService → greetingTemplate` nghĩa là **service phụ thuộc template**, không phải service phải giữ reference tới context. Bean có thể được tạo theo thứ tự bảo đảm dependency cần thiết, không phải theo vị trí method trên file.

### Bean definition khác bean instance

- **Bean definition**: metadata mà container lưu về tên, factory method, kiểu/scope có thể xác định, cách tạo và các thiết lập liên quan.
- **Bean instance**: đối tượng thực tế do factory method cung cấp/được container quản lý. Nó không tự xuất hiện chỉ vì source có annotation; lớp cấu hình phải **được đăng ký và xử lý**.
- **Dependency injection**: cung cấp collaborator vào constructor, tham số factory hoặc điểm injection được hỗ trợ.
- **Scope mặc định**: `singleton` theo **một bean definition trong một context**, không phải một instance toàn JVM.

Với context thông thường, singleton **không lazy** được tạo trong giai đoạn refresh; bean lazy/prototype có quy tắc thời điểm khởi tạo khác. Không được kết luận mọi bean đã tạo tức thì ngay khi registry nhận metadata.

## 4. Tạo dự án Maven bằng Java 17

Dùng **Spring Framework 7.0.9** cùng mốc với phần chuẩn bị dependency; đây là **phiên bản được ghim để tái lập**, không phải khẳng định phiên bản mới nhất. Cần JDK 17 trở lên và Maven. Không cần Spring Boot.

Cấu trúc thư mục:

~~~text
minimal-spring-config/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── MinimalConfigDemo.java
~~~

File package `demo` nằm ở thư mục `src/main/java/demo/`. Java yêu cầu tên tệp khớp public top-level class `MinimalConfigDemo`.

### 4.1. pom.xml

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>minimal-spring-config</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>7.0.9</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.14.1</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.1</version>
                <configuration>
                    <mainClass>demo.MinimalConfigDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Module `spring-context` dùng các dependency transitively từ các Spring modules liên quan. Maven tải chúng tự động; đừng tự chép một JAR rời rồi cho rằng classpath đã đầy đủ. Khi offline, Maven chỉ chạy nếu các artifacts cần thiết có sẵn trong cache.

### 4.2. File Java hoàn chỉnh

Tạo `src/main/java/demo/MinimalConfigDemo.java`:

~~~java
package demo;

import java.util.Objects;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public final class MinimalConfigDemo {
    private MinimalConfigDemo() {}

    public interface GreetingTemplate {
        String render(String name);
    }

    public static final class VietnameseTemplate
            implements GreetingTemplate {
        @Override
        public String render(String name) {
            return "Xin chao, " + name + "!";
        }
    }

    public static final class GreetingService {
        private final GreetingTemplate template;

        public GreetingService(GreetingTemplate template) {
            this.template = Objects.requireNonNull(
                    template, "template must not be null");
        }

        public String greet(String rawName) {
            if (rawName == null || rawName.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
            return template.render(rawName.strip());
        }

        public GreetingTemplate template() {
            return template;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {
        @Bean
        public GreetingTemplate greetingTemplate() {
            return new VietnameseTemplate();
        }

        @Bean
        public GreetingService greetingService(
                GreetingTemplate template) {
            return new GreetingService(template);
        }
    }

    public static void main(String[] args) {
        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(AppConfig.class);

        try (context) {
            boolean definitions =
                    context.containsBeanDefinition("greetingTemplate")
                    && context.containsBeanDefinition("greetingService");

            GreetingTemplate template =
                    context.getBean(GreetingTemplate.class);
            GreetingService service =
                    context.getBean(GreetingService.class);

            System.out.println("definitions=" + definitions);
            System.out.println("injectedSame=" +
                    (service.template() == template));
            System.out.println("serviceSingleton=" +
                    (service == context.getBean(GreetingService.class)));
            System.out.println("greeting=" +
                    service.greet(" Lan "));
        }

        System.out.println("contextActiveAfterClose=" + context.isActive());
    }
}
~~~

**Tính runnable:** chỉ có một public top-level class `MinimalConfigDemo`; các type khác nằm bên trong. `AppConfig` được khai báo là nested class `public static` nên Spring có thể sử dụng làm configuration class. `greetingService(GreetingTemplate)` yêu cầu một collaborator và context có đúng một bean phù hợp.

**Về `try (context)`:** Java 17 cho phép sử dụng biến local effectively final làm tài nguyên, và Spring `AnnotationConfigApplicationContext` có thể đóng qua `close()`. Sau khối, `context.isActive()` trả `false`; không tiếp tục lấy/dùng bean bằng một context đã đóng.

### 4.3. Lệnh chạy bằng Maven và expected output

Trong thư mục `minimal-spring-config`:

~~~bash
java -version
javac -version
mvn -version
mvn -q clean compile exec:java
~~~

**Năm dòng stdout do phương thức `main` của ứng dụng in khi thành công:**

~~~text
definitions=true
injectedSame=true
serviceSingleton=true
greeting=Xin chao, Lan!
contextActiveAfterClose=false
~~~

Ý nghĩa:

- `definitions=true`: context đã đăng ký **hai bean names của ứng dụng**. Không suy ra tổng số bean definitions trong context chỉ bằng hai (có thể có infrastructure definitions).
- `injectedSame=true`: reference template mà service giữ chính là bean `GreetingTemplate` context cung cấp.
- `serviceSingleton=true`: lấy bean `GreetingService` hai lần trong cùng context vẫn là cùng instance theo default singleton.
- `greeting=...`: hành vi từ method của service/template Java thông thường, không phải annotation tự động tạo câu chào.
- `contextActiveAfterClose=false`: context không còn active sau khi đóng.

Kết quả lệnh `java -version`, `javac -version`, `mvn -version` và bất kỳ log phụ Maven nào không nằm trong năm dòng stdout ứng dụng. Các phép kiểm tra reference `==` được dùng có chủ đích để chứng minh **identity của singleton ở cấu hình này**, không phải quy tắc cho mọi scope hoặc mọi proxy.

### 4.4. Tự biên dịch bằng javac / chạy bằng java

Mã dùng Spring nên cần các JAR liên quan trong classpath. Tạo danh sách classpath từ Maven:

~~~bash
mvn -q dependency:build-classpath -Dmdep.outputFile=classpath.txt
~~~

Linux/macOS (bash hoặc zsh):

~~~bash
mkdir -p out
javac --release 17 -encoding UTF-8 \
  -cp "$(cat classpath.txt)" \
  -d out src/main/java/demo/MinimalConfigDemo.java
java -cp "out:$(cat classpath.txt)" demo.MinimalConfigDemo
~~~

Windows PowerShell:

~~~powershell
$deps = (Get-Content -Raw classpath.txt).Trim()
New-Item -ItemType Directory -Force out | Out-Null
javac --release 17 -encoding UTF-8 -cp "$deps" -d out src/main/java/demo/MinimalConfigDemo.java
java -cp "out;$deps" demo.MinimalConfigDemo
~~~

Trong classpath, Unix phân tách entry bằng dấu `:`, Windows bằng dấu `;`. Lệnh `javac --release 17` biên dịch với Java 17 API/language target; launcher chạy class `demo.MinimalConfigDemo`. Nếu gặp `package org.springframework... does not exist`, nguyên nhân đầu tiên cần kiểm tra là **classpath/dependency**, không phải “Spring không inject được”.

## 5. Spring đã làm gì ở thời điểm startup?

Container nhận `AppConfig.class` khi khởi tạo:

~~~java
new AnnotationConfigApplicationContext(AppConfig.class)
~~~

Đây là đoạn rút ra từ file demo, dùng khi tạo context. Về logic:

1. Spring xử lý `@Configuration`, đăng ký các bean definitions được khai báo bằng `@Bean` (cùng những infrastructure definitions cần thiết).
2. Khi cần tạo singleton `greetingTemplate`, factory method trả `VietnameseTemplate`.
3. Khi cần tạo singleton `greetingService`, Spring thấy factory method yêu cầu `GreetingTemplate`, resolve bean tương ứng và truyền nó vào.
4. Factory method xây dựng `GreetingService(template)`. Service giữ reference đó trong trường `final`.
5. Ứng dụng lấy service qua `context.getBean(GreetingService.class)` và gọi `greet(" Lan ")`.
6. Khi đóng context, Spring thực hiện các callback hủy/đóng phù hợp của các managed singleton. Trong demo không có tài nguyên I/O do service/template giữ.

~~~mermaid
sequenceDiagram
    participant Main as main
    participant Context as SpringContext
    participant Config as AppConfig
    participant Template as GreetingTemplate
    participant Service as GreetingService
    Main->>Context: new Context(AppConfig.class)
    Context->>Config: process @Configuration / @Bean
    Context->>Config: greetingTemplate()
    Config-->>Context: VietnameseTemplate
    Context->>Config: greetingService(template)
    Config-->>Context: GreetingService
    Main->>Context: getBean(GreetingService.class)
    Context-->>Main: singleton service
    Main->>Service: greet(" Lan ")
    Service->>Template: render("Lan")
    Template-->>Service: "Xin chao, Lan!"
    Main->>Context: close()
~~~

Đây là **mô hình minh họa luồng phụ thuộc**, không phải quy định logger hoặc mọi bean có chung thứ tự như thế. Thứ tự hai bean ở đây có quan hệ dependency: service cần template. Hai bean độc lập không có thứ tự khởi tạo tổng quát cố định dựa vào tên method.

**BeanFactory vs ApplicationContext:** `BeanFactory` là giao diện nền tảng truy xuất bean; `ApplicationContext` mở rộng nó bằng lifecycle, message resources, application events và những tiện ích khác. Ứng dụng thực tế thường chọn `ApplicationContext`. Spring không yêu cầu service phụ thuộc trực tiếp vào `ApplicationContext`; service chỉ cần `GreetingTemplate`.

## 6. @Configuration mặc định và proxyBeanMethods=false

Có thể thấy hai cách khai báo:

~~~java
@Configuration
class DefaultConfig {
    @Bean
    GreetingTemplate greetingTemplate() {
        return new VietnameseTemplate();
    }

    @Bean
    GreetingService greetingService() {
        return new GreetingService(greetingTemplate());
    }
}
~~~

Đây là **mã minh họa không độc lập**. `@Configuration` mặc định có `proxyBeanMethods=true`: Spring tăng cường/intercept các lời gọi thích hợp giữa `@Bean` methods trong cấu hình được quản lý, để giữ bean semantics như singleton.

Trong demo runnable, ta dùng:

~~~java
@Configuration(proxyBeanMethods = false)
class AppConfig {
    @Bean
    GreetingTemplate greetingTemplate() {
        return new VietnameseTemplate();
    }

    @Bean
    GreetingService greetingService(GreetingTemplate template) {
        return new GreetingService(template);
    }
}
~~~

Ở chế độ `proxyBeanMethods=false`, lời gọi **trực tiếp** từ một factory method đến method `@Bean` khác là lời gọi Java bình thường, **không được intercept** để trả bean scoped tương ứng. Ví dụ:

~~~java
@Bean
GreetingService wrongStyle() {
    return new GreetingService(greetingTemplate());
}
~~~

Nếu cấu hình áp dụng chế độ `false`, `greetingTemplate()` ở đây tự chạy như method Java và có thể tạo một object khác với bean `greetingTemplate` trong context. Điều đó phá kỳ vọng collaborator được chia sẻ, nhất là khi template có trạng thái/tài nguyên.

**Lựa chọn dễ hiểu cho người mới:** truyền dependency qua tham số factory method, như `greetingService(GreetingTemplate template)`; Spring tự resolve bean. Cách này làm mối quan hệ phụ thuộc rõ ràng và không cần gọi trực tiếp factory method khác. Không chọn `proxyBeanMethods=false` mù quáng nếu configuration của bạn phụ thuộc vào inter-bean method calls.

## 7. Tên bean, kiểu, scope và các biến thể

Mặc định `@Bean` dùng tên method:

~~~java
@Bean
GreetingService greetingService(GreetingTemplate template) { ... }
~~~

Đây là **mẫu chữ ký**, không phải Java source có thể compile nguyên văn vì có dấu `...`. Trong code thực tế, thân method phải trả về object phù hợp.

Có thể đặt tên khác:

~~~java
@Bean("greetingServicePrimary")
GreetingService buildGreetingService(GreetingTemplate template) {
    return new GreetingService(template);
}
~~~

Lúc đó `getBean("greetingServicePrimary")` tra bằng tên mới; không nên nhầm tên **Java method**, tên **bean definition** và kiểu **GreetingService**. Chỉ dùng phương thức có tên/đăng ký đúng trong một cấu hình cụ thể, tránh copy thêm snippet vào demo rồi trùng đăng ký.

Một số API tra cứu:

~~~java
GreetingService s1 = context.getBean(GreetingService.class);
Object s2 = context.getBean("greetingService");
boolean exists = context.containsBeanDefinition("greetingService");
~~~

Đây là **snippet** giả sử có biến context hợp lệ; type lookup cần giải quyết ứng viên. Khi chỉ có một `GreetingService` trong demo, `getBean(GreetingService.class)` thành công.

### Scope không đồng nghĩa static

- `singleton` là scope mặc định: một bean instance cho một bean definition trong một context, trừ các cơ chế/proxy đặc biệt làm cần hiểu thêm identity.
- `prototype` có thể tạo instance mới khi có yêu cầu phù hợp. Nếu inject prototype vào singleton bằng cách thông thường, dependency được resolve khi singleton được khởi tạo, không tự đổi prototype ở mọi method call.
- `@Lazy` có thể hoãn khởi tạo khi chưa cần; không xóa dependency logic.
- Scope theo request/session cần môi trường web tương ứng; project Java thuần ở đây không có sẵn chúng.

Đừng đánh đồng singleton bean với **lớp có trường static** hoặc một global instance toàn JVM. Hai Spring contexts có thể sở hữu hai singleton khác nhau của cùng một bean definition logic.

## 8. Lỗi thường gặp và cách chẩn đoán

| Hiện tượng | Vì sao | Cách kiểm tra |
| --- | --- | --- |
| `cannot find symbol` / `package org.springframework... does not exist` | Tên/import/classpath/dependency compile sai | Kiểm tra JDK, Maven pom, `javac -cp`, package |
| Context không tìm thấy `GreetingService` | Class cấu hình chưa được nạp hoặc không có method `@Bean` hợp lệ | Kiểm tra `AppConfig.class` khi tạo context và method registration |
| `UnsatisfiedDependencyException` | Service cần bean `GreetingTemplate` nhưng thiếu ứng viên | Đăng ký collaborator hoặc sửa kiểu factory parameter |
| `NoUniqueBeanDefinitionException` | Hai/nhiều ứng viên cho một injection point cần một | Chọn bằng `@Qualifier`/`@Primary` hoặc giảm bean trùng |
| `BeanCreationException` | Factory method, constructor hoặc init thất bại | Đọc sâu `Caused by` và method/class đang tạo |
| `BeanCurrentlyInCreationException` | Có chu trình constructor/factory dependencies | Vẽ A→B→A, thiết kế lại dependency |
| Service tạo bằng `new` ngoài context không có lifecycle Spring | Object không được container quản lý | Inject thủ công hoặc lấy managed bean qua context |
| Cùng type có hai bean do đăng ký `@Component` lẫn `@Bean` | Đăng ký trùng không chủ đích | Chọn một cơ chế hoặc chỉ định candidate rõ |
| `injectedSame=false` sau thay đổi | Tự gọi `@Bean` method ở chế độ `proxyBeanMethods=false` | Dùng tham số factory method để Spring resolve |
| `getBean` thất bại khi context đã đóng | Sử dụng container sau shutdown | Dùng trong vòng đời context, đóng đúng nơi |
| Output lời chào sai dù context chạy | Logic Java/dữ liệu template sai | Unit test service/template, không quy mọi lỗi cho Spring |

### Phép thử âm đơn giản

1. **Thiếu bean:** xóa/không đăng ký method `@Bean GreetingTemplate greetingTemplate()` trong một cấu hình thử riêng. Vì `greetingService(GreetingTemplate)` yêu cầu dependency, context thường thất bại khi tạo service. Kiểm tra lỗi thiếu bean, không tự chèn null để chạy qua.
2. **Hai ứng viên:** thêm hai bean cùng kiểu `GreetingTemplate` rồi giữ injection point đơn trị không qualifier/primary. Nếu quy tắc chọn không có lựa chọn duy nhất, Spring báo mơ hồ. Lưu ý các quy tắc name/qualifier/primary có thể khiến một số trường hợp vẫn được giải quyết; không khẳng định cứ có hai bean là luôn thất bại.
3. **Chưa đăng ký config:** có class chứa `@Configuration` trong source nhưng context không được cung cấp class/scan/import tương ứng. Annotation không tự làm bean xuất hiện.
4. **Đổi tên method:** một lookup theo name có thể hỏng dù lookup theo type vẫn hoạt động. Hãy kiểm tra chính xác bean name thực tế.

Các thử nghiệm âm nên tách khỏi happy-path project. Không cần đóng đinh toàn bộ văn bản stack trace vì thông điệp và wrapper có thể thay đổi theo JDK/Spring/config.

### Đọc lỗi từ gốc

Lỗi ở service có thể chỉ là hiệu ứng của factory method tạo template thất bại. Trong exception chain, xác định bean name, injection point, factory method và **nguyên nhân đầu tiên có ý nghĩa**. Nếu `mvn` fail do không tải được dependency, đó chưa phải lỗi bean wiring. Nếu `javac` báo thiếu package Spring, chưa tới bước context xử lý annotation.

## 9. Kiểm thử logic và kiểm thử cấu hình

**Unit test Java thuần:** dùng fake template, không cần Spring:

~~~java
GreetingTemplate fake = name -> "TEST: " + name;
GreetingService service = new GreetingService(fake);
System.out.println(service.greet(" An ")); // TEST: An
~~~

Đây là đoạn minh họa trong phạm vi có quyền truy cập các nested types, không phải file độc lập. Nó chứng minh service có thể dùng collaborator khác do caller cung cấp.

**Smoke test Spring wiring:** khởi tạo context với cấu hình thật, lấy bean và kiểm tra reference:

~~~java
try (AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(AppConfig.class)) {
    GreetingTemplate expected = context.getBean(GreetingTemplate.class);
    GreetingService actual = context.getBean(GreetingService.class);
    if (actual.template() != expected) {
        throw new IllegalStateException("unexpected injection");
    }
}
~~~

Đây cũng là snippet đặt trong method/class có import và quyền truy cập đầy đủ. Nó kiểm tra **cấu hình của container**, không chứng minh mọi hành vi nghiệp vụ hoặc mọi môi trường chạy đều đúng.

Một unit test PASS không chứng minh Spring đã đăng ký bean; ngược lại context refresh thành công không chứng minh logic câu chào đúng. Trong project thật, có thể đưa cả hai nhóm vào test tự động (ví dụ JUnit/Spring Test) khi công việc đủ lớn.

### Không dùng Java assert để thay kiểm tra bắt buộc

Java `assert` mặc định có thể bị tắt nếu không bật `-ea`. Nếu mục tiêu là **bắt buộc phát hiện sai wiring** trong test/ứng dụng, dùng assertion của test framework hoặc kiểm tra thường ném exception. Phần demo này in các giá trị boolean để quan sát; trong hệ thống nghiêm túc, nên **fail test** khi giá trị không đúng, không chỉ ghi log rồi tiếp tục.

## 10. Bài tập có đáp án

**Bài 1.** Một method `@Bean GreetingService greetingService(GreetingTemplate template)` được gọi như thế nào khi chỉ có một bean kiểu `GreetingTemplate`?

**Đáp án:** Spring resolve bean tương ứng theo kiểu/quy tắc injection, truyền nó vào tham số factory method và gọi method để lấy service. Bạn không phải tự gọi `greetingTemplate()`.

**Bài 2.** Nếu đã có `@Bean greetingService(...)` thì có cần thêm `@Component` cho `GreetingService` không?

**Đáp án:** Không. `@Bean` đã đăng ký factory tạo service; đăng ký thêm bằng scanning không chủ đích có thể tạo ứng viên trùng hoặc ambiguity.

**Bài 3.** Vì sao `serviceSingleton=true` trong demo?

**Đáp án:** `@Bean` mặc định có scope singleton trong cùng context. Hai lần `getBean(GreetingService.class)` trả về cùng instance, không phải vì class có trường static.

**Bài 4.** `@Configuration(proxyBeanMethods=false)` có intercept `greetingTemplate()` khi một method khác gọi trực tiếp nó không?

**Đáp án:** Không. Khi gọi trực tiếp, đó là lời gọi Java thường và có thể tạo thêm instance, vì vậy ví dụ chuẩn khai báo dependency ở tham số method.

**Bài 5.** Có thể tạo app Spring bằng chỉ `new AnnotationConfigApplicationContext(AppConfig.class)` không có Spring Boot không?

**Đáp án:** Có. Đây là Spring Framework Core Container thuần; module `spring-context`, cấu hình Java và JDK/Maven đủ cho ví dụ này.

**Bài 6.** Tại sao `context.containsBeanDefinition("greetingService")` thích hợp hơn việc khẳng định toàn context chỉ có hai bean definitions?

**Đáp án:** Context còn có thể có infrastructure definitions. Chỉ hai method `@Bean` của ứng dụng được kiểm tra theo **tên mục tiêu**, không phải tổng đếm tất cả definitions.

**Bài 7.** Một object `new GreetingService(new VietnameseTemplate())` ngoài context có phải Spring bean?

**Đáp án:** Không, đó là object Java thường, dù constructor injection bằng tay vẫn đúng. Nó không tự nhận lifecycle/scope của container.

## 11. Checklist hoàn thành bài

- [ ] Dùng đúng JDK 17+, Maven và module `spring-context`.
- [ ] Tên `pom.xml`, package, tên file, class public nhất quán.
- [ ] `AppConfig` có `@Configuration` và được nạp vào context.
- [ ] Hai factory methods dùng `@Bean` và có tên/kiểu rõ.
- [ ] `GreetingService` nhận `GreetingTemplate` qua constructor, không tự lấy bean từ context.
- [ ] `greetingService(GreetingTemplate)` dùng tham số do Spring resolve, không tự gọi `greetingTemplate()`.
- [ ] Hiểu default singleton và ý nghĩa của `proxyBeanMethods=false`.
- [ ] Chạy `mvn -q clean compile exec:java`, đối chiếu 5 dòng stdout.
- [ ] Nếu chạy `javac`/`java` trực tiếp, có classpath đủ JAR của Spring.
- [ ] Đóng context khi app sở hữu lifecycle nó và không dùng context sau close.
- [ ] Phân biệt kiểm tra registration, wiring, behavior và lỗi classpath.
- [ ] Thử ca thiếu bean/nhầm tên/cấu hình không được nạp trong test riêng.

**Kết luận:** Với `@Configuration`, Spring biết **nguồn cấu hình**; với `@Bean`, Spring biết **factory method cung cấp đối tượng**. `AnnotationConfigApplicationContext` xử lý metadata, resolve tham số phụ thuộc, tạo/giữ bean theo scope và cho ứng dụng dùng bean đó. Bạn đã có một ứng dụng Spring Core Container đầu tiên mà **không cần Spring Boot**.

### Tài liệu Spring chính thức

- [Java-based Container Configuration](https://docs.spring.io/spring-framework/reference/core/beans/java.html)
- [Using @Bean](https://docs.spring.io/spring-framework/reference/core/beans/java/bean-annotation.html)
- [Using @Configuration](https://docs.spring.io/spring-framework/reference/core/beans/java/configuration-annotation.html)
- [Dependency Injection and Collaborators](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="first-application-004">Khởi tạo AnnotationConfigApplicationContext</a>

<details>
<summary>Xem chi tiết</summary>

# Khởi tạo `AnnotationConfigApplicationContext` trong Spring Framework

## 1. Mục tiêu và ý nghĩa của context

`AnnotationConfigApplicationContext` là một **implementation của `ApplicationContext`** trong `spring-context`, dùng để khởi động Spring IoC container từ cấu hình annotation/Java, không cần file XML. Nó nhận những class cấu hình `@Configuration`, các class thành phần (`@Component`, `@Service`...) hoặc danh sách package cần component scan, sau đó đăng ký **bean definitions**, tạo/wire những bean phù hợp và cung cấp API `getBean(...)`.

Trong bài này, chúng ta học chính xác **lúc nào context được khởi tạo, lúc nào cần `refresh()`, cách lấy bean và cách đóng context**. Đây là một chương trình **Spring Framework thuần**, không dùng `SpringApplication.run(...)`, Spring Boot hay web server.

Kết quả mong muốn:
- Khởi tạo context với `new AnnotationConfigApplicationContext(AppConfig.class)`.
- Hiểu cách làm tương đương bằng `new AnnotationConfigApplicationContext()`, sau đó `register(AppConfig.class)` và `refresh()`.
- Biết khi nào sử dụng `scan("demo.components")`, và vì sao chỉ gắn `@Component` chưa làm bean tự xuất hiện.
- Tra `getBean(Class<T>)`, `containsBean(...)`, kiểm tra singleton và đóng container bằng `try-with-resources`.
- Chẩn đoán lỗi quên `refresh()`, quét nhầm package, trùng bean theo type, thiếu dependency và gọi `refresh()` sai vòng đời.

Điều kiện thực hành: **JDK 17+**, **Maven 3.9+**, truy cập Maven Central/mirror để tải dependency lần đầu. Ví dụ chọn Spring Framework **7.0.9**; Spring 7.0 giữ baseline Java 17. Phiên bản này là mốc tái hiện bài học, không phải yêu cầu dùng cố định trong sản phẩm lâu dài.

## 2. `AnnotationConfigApplicationContext` đứng ở đâu?

Các lớp/giao diện liên quan:

| Loại | Vai trò |
| --- | --- |
| `BeanFactory` | API cơ bản để truy cập bean do container quản lý |
| `ApplicationContext` | Mở rộng khả năng bean factory bằng sự kiện, resource, message source... |
| `ConfigurableApplicationContext` | Hợp đồng cấu hình/quản lý lifecycle của context (gồm `refresh()`, `close()`) |
| `AnnotationConfigApplicationContext` | Implementation thực tế đọc cấu hình annotation, hỗ trợ `register`, `scan`, `registerBean` |

Package cần import khi viết code:

~~~java
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
~~~

Ba dòng trên là **đoạn import minh họa**, được dùng trong chương trình hoàn chỉnh ở mục 5. Chúng không phải một chương trình Java độc lập.

`@Bean` khai báo một **factory method** trả về object được Spring quản lý. `@Configuration` đánh dấu class làm nguồn configuration. `ApplicationContext` là container sử dụng metadata đó để tạo, gắn dependency và quản lý bean; nó **không** tự tạo mọi object Java trên classpath.

Phân biệt:
- `new AnnotationConfigApplicationContext(...)` tạo **container/context**, không phải một service nghiệp vụ.
- `context.getBean(GreetingService.class)` lấy **bean của service** đã được đăng ký và tạo theo scope.
- `new GreetingService(...)` tự tạo Java object bên ngoài Spring; không tự tham gia lifecycle/DI của container.
- `@Component` trên class chỉ thành bean khi class được **đăng ký/scan** qua context phù hợp.

## 3. Ba cách khởi tạo: chỗ nào tự `refresh()`?

| Cách dùng | Context tự `refresh()`? | Khi phù hợp |
| --- | --- | --- |
| `new AnnotationConfigApplicationContext(AppConfig.class)` | **Có** | Có sẵn một hoặc nhiều class cấu hình, muốn mở nhanh |
| `new AnnotationConfigApplicationContext("demo.components")` | **Có** | Biết rõ package để quét, muốn quét ngay khi tạo |
| `new AnnotationConfigApplicationContext()` rồi `register(...)` | **Không**: bạn phải gọi `refresh()` | Muốn kết hợp nhiều nguồn cấu hình hoặc chuẩn bị context trước bootstrap |
| `new AnnotationConfigApplicationContext()` rồi `scan(...)` | **Không**: bạn phải gọi `refresh()` | Muốn chủ động chọn package scan trước bootstrap |

Ví dụ **tóm tắt cú pháp**, sử dụng `AppConfig` được khai báo ở mục 5:

~~~java
// Cách nhanh: constructor nhận class tự refresh trước khi trả về.
try (var ctx = new AnnotationConfigApplicationContext(AppConfig.class)) {
    var service = ctx.getBean(GreetingService.class);
}

// Cách từng bước: bắt buộc tự refresh.
try (var ctx = new AnnotationConfigApplicationContext()) {
    ctx.register(AppConfig.class);
    ctx.refresh();
    var service = ctx.getBean(GreetingService.class);
}
~~~

Khối trên minh họa **đặt trong một phương thức có đúng import/type tương ứng**. Ở phần demo hoàn chỉnh, nó được triển khai thành class Java có thể biên dịch.

Một constructor `new AnnotationConfigApplicationContext(AppConfig.class, MoreConfig.class)` cũng có thể nhận nhiều class. `register(...)` tương tự có thể được gọi nhiều lần **trước khi** `refresh()`. Sau khi bootstrap xong, hãy làm việc với context đang active thay vì cố coi context này là loại refresh lại vô hạn.

**Quan trọng:** `AnnotationConfigApplicationContext` kế thừa `GenericApplicationContext`, có semantics **refresh một lần** cho một context. Đừng gọi `refresh()` lần hai sau khi constructor nhận cấu hình đã tự refresh hoặc sau lần refresh thủ công. Muốn xây lại container từ nguồn mới, thông thường tạo context mới.

## 4. Bên trong bước `refresh()`

Mô hình đơn giản hóa:

~~~text
Tạo context rỗng / truyền cấu hình lúc constructor
                     |
        Đăng ký config class hoặc scan
                     |
      Xác định bean-definition metadata
                     |
                refresh()
                     |
     Chạy các bước hậu xử lý metadata
                     |
       Resolve dependency và scope
                     |
       Khởi tạo bean singleton không lazy
                     |
          Gắn dependency / init hooks
                     |
             Context sẵn sàng
                     |
        getBean(...) / gọi nghiệp vụ
                     |
       close() / destruction callbacks
~~~

`refresh()` không chỉ là “đổi một cờ ready”: đây là **bước bootstrap** cấu hình bean factory, xử lý các định nghĩa cấu hình, đăng ký post-processors, tạo singleton không lazy, phát sự kiện lifecycle và hoàn tất các bước cần thiết để sử dụng context. Các scope và lazy configuration có thể làm một số instance **chưa được tạo ngay** lúc refresh; definition có thể tồn tại trước instance.

Lời gọi `register(AppConfig.class)` chỉ chỉ định **nguồn cấu hình**; các `@Bean` methods chưa nhất thiết được xử lý và bean chưa sẵn sàng để lấy khi chưa refresh.

Với constructor nhận `AppConfig.class`, quá trình `register` và `refresh` diễn ra bên trong constructor **trước khi nó trả về**. Vì vậy, sau dòng `new AnnotationConfigApplicationContext(AppConfig.class)`, việc gọi `getBean` ngay là hợp lệ nếu bootstrap thành công.

**Không đồng nhất thời điểm `refresh()` với thời điểm tạo mọi object.** Bean `@Lazy` và prototype có quy tắc riêng; `getBean` có thể tạo prototype mới, còn singleton mặc định được chia sẻ.

## 5. Dự án Maven Java 17 chạy độc lập: constructor so với `register + refresh`

### 5.1. Cấu trúc

~~~text
context-bootstrap-demo/
├── pom.xml
└── src/main/java/
    └── demo/
        ├── ContextBootstrapDemo.java
        ├── ScanBootstrapDemo.java
        └── components/
            └── ScanGreeting.java
~~~

Đây là **một dự án Maven**, có hai main class. Bạn có thể chạy lần lượt, không cần đổi POM hay thêm Spring Boot. `ScanGreeting` phục vụ cách scan ở mục 6.

### 5.2. `pom.xml`

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>context-bootstrap-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>7.0.9</version>
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

`spring-context` có thể kéo theo những Spring artifacts nền như `spring-core`, `spring-beans` và `spring-expression` theo metadata Maven. Bạn không phải tự thêm `spring-boot-starter`. Maven Compiler Plugin dùng `--release 17` theo property đã khai báo. Exec Maven Plugin thực thi main class được chỉ định bằng `-Dexec.mainClass`.

### 5.3. `src/main/java/demo/ContextBootstrapDemo.java`

~~~java
package demo;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public final class ContextBootstrapDemo {

    private ContextBootstrapDemo() {
    }

    interface Greeter {
        String greet(String name);
    }

    static final class DefaultGreeter implements Greeter {
        @Override
        public String greet(String name) {
            return "Hello, " + name + "!";
        }
    }

    static final class GreetingService {
        private final Greeter greeter;

        GreetingService(Greeter greeter) {
            this.greeter = greeter;
        }

        String greet(String name) {
            return greeter.greet(name);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class AppConfig {
        @Bean
        Greeter greeter() {
            return new DefaultGreeter();
        }

        @Bean
        GreetingService greetingService(Greeter greeter) {
            return new GreetingService(greeter);
        }
    }

    private static void inspect(
            String label, AnnotationConfigApplicationContext ctx) {
        GreetingService service = ctx.getBean(GreetingService.class);

        System.out.println(label + ".message=" + service.greet("An"));
        System.out.println(label + ".singleton="
                + (service == ctx.getBean(GreetingService.class)));
        System.out.println(label + ".active=" + ctx.isActive());
    }

    public static void main(String[] args) {
        // Cách 1: constructor nhận config class tự refresh.
        try (var direct =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {
            inspect("direct", direct);
        }

        // Cách 2: tạo context, register, rồi refresh một lần.
        try (var manual = new AnnotationConfigApplicationContext()) {
            manual.register(AppConfig.class);
            manual.refresh();
            inspect("manual", manual);
        }
    }
}
~~~

**Diễn giải code:**

- `AppConfig` được đăng ký như một nguồn cấu hình. Hai factory methods `@Bean` tạo `Greeter` và `GreetingService`.
- `greetingService(Greeter greeter)` nhận phụ thuộc từ container; nó không gọi trực tiếp `greeter()`, phù hợp với `proxyBeanMethods=false`.
- Trong `inspect`, `getBean(GreetingService.class)` lấy bean từ context tương ứng. Vì scope mặc định là singleton, **hai lần lấy trong cùng context** trả cùng reference.
- `direct` và `manual` là **hai context độc lập**, không có nghĩa chúng chia sẻ singleton. Việc mỗi context tạo một bean graph riêng là đúng.
- `isActive()` cho biết context đang active khi gọi `inspect`; `close()` xảy ra sau các lệnh in vì `try-with-resources` tự quản lý tài nguyên.

### 5.4. Build, chạy và expected stdout

Từ thư mục chứa `pom.xml`, kiểm tra:

~~~bash
java -version
javac -version
mvn -version
~~~

Chạy main class thứ nhất; dấu nháy quanh `-Dexec.mainClass=...` dùng được trên Bash và PowerShell:

~~~bash
mvn -q clean compile exec:java "-Dexec.mainClass=demo.ContextBootstrapDemo"
~~~

**Sáu dòng stdout từ chính chương trình** (không tính output của `java -version`/`mvn -version`, log build hoặc logging backend):

~~~text
direct.message=Hello, An!
direct.singleton=true
direct.active=true
manual.message=Hello, An!
manual.singleton=true
manual.active=true
~~~

Không có cổng HTTP được mở. Chương trình kết thúc sau khi hai context lần lượt được đóng. Các log cấu hình Spring có thể xuất hiện tùy môi trường, nhưng không nằm trong sáu dòng do `System.out.println` tạo ra.

Nếu `javac` báo không tìm thấy `org.springframework.context.annotation`, bạn có thể đã compile thủ công không kèm classpath. Hãy chạy từ thư mục đúng, qua Maven để các dependency transitive được resolve.

## 6. Dự án trên, cách thứ ba: component scan

Với component scan, không nhất thiết phải biết trước từng class cụ thể, nhưng bạn **phải chỉ định đúng package** và class cần scan phải có metadata thích hợp (ví dụ `@Component`).

### 6.1. `src/main/java/demo/components/ScanGreeting.java`

~~~java
package demo.components;

import org.springframework.stereotype.Component;

@Component
public class ScanGreeting {
    public String message() {
        return "scanned bean ready";
    }
}
~~~

### 6.2. `src/main/java/demo/ScanBootstrapDemo.java`

~~~java
package demo;

import demo.components.ScanGreeting;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public final class ScanBootstrapDemo {
    private ScanBootstrapDemo() {
    }

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.scan("demo.components");
            context.refresh();

            ScanGreeting greeting = context.getBean(ScanGreeting.class);
            System.out.println("scan.message=" + greeting.message());
            System.out.println("scan.name="
                    + context.containsBean("scanGreeting"));
            System.out.println("scan.active=" + context.isActive());
        }
    }
}
~~~

`scan("demo.components")` tìm kiếm candidate components trong package được yêu cầu và các package con; nó không tự quét toàn bộ classpath. Class `ScanGreeting` có `@Component`, nên sẽ được đăng ký trong context. Tên mặc định của bean theo component scan ở đây là `scanGreeting` (với cách tạo tên mặc định của Spring).

Chạy lần thứ hai bằng cùng POM:

~~~bash
mvn -q compile exec:java "-Dexec.mainClass=demo.ScanBootstrapDemo"
~~~

**Ba dòng stdout của chương trình thứ hai:**

~~~text
scan.message=scanned bean ready
scan.name=true
scan.active=true
~~~

Dùng constructor có package cũng được: `new AnnotationConfigApplicationContext("demo.components")` sẽ **scan và refresh tự động**. Khi cần điều chỉnh nhiều package hoặc phối hợp `register` và `scan`, tạo context rỗng rồi gọi `refresh()` một lần thường dễ đọc hơn.

**Giới hạn:** `@Component` chỉ có hiệu lực khi class được đăng ký hoặc tìm thấy bởi scan của context hiện hành. Nếu dùng `scan("demo.unrelated")` thay cho package thực tế, `getBean(ScanGreeting.class)` sẽ không tìm được bean. Không phải lỗi cú pháp Java; đó là lỗi cấu hình bean graph.

## 7. Lifecycle: đóng context và xử lý sự cố lúc bootstrap

`AnnotationConfigApplicationContext` hỗ trợ `AutoCloseable` qua hợp đồng context, nên `try-with-resources` giúp gọi `close()` khi thoát khối, kể cả khi phát sinh exception trong phần sử dụng.

~~~java
// Đoạn minh họa đặt trong một phương thức với AppConfig và import phù hợp.
try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
    // Use beans here.
} // close() được gọi tự động.
~~~

Đây là đoạn minh họa **không độc lập**; chương trình hoàn chỉnh đã có ở mục 5.

**Đóng context không có nghĩa Java GC ngay lập tức xóa tất cả object.** Spring thực hiện destruction callbacks cho các bean được quản lý phù hợp (đặc biệt singleton); reference Java còn được giữ ở nơi khác vẫn tuân theo quy tắc lifetime/GC thông thường. Prototype có quy tắc destruction khác: container thường không tự quản lý đầy đủ bước hủy sau khi cấp instance.

Một context có thể **khởi động thất bại** nếu:
- Class cấu hình không hợp lệ hoặc factory method ném exception.
- Thiếu bean cần thiết cho constructor/`@Bean` factory method parameter.
- Có nhiều bean candidates cho một dependency type nhưng không có cách phân giải phù hợp.
- Constructor dependencies tạo vòng không thể giải quyết.
- Component scan không tìm thấy thành phần mà ứng dụng kỳ vọng.

Khi gặp lỗi trong constructor đã refresh (chẳng hạn `new AnnotationConfigApplicationContext(AppConfig.class)`), constructor có thể **ném exception trước khi gán biến context**. Đừng dựa vào việc lấy context từ constructor không hoàn tất để gọi `close()`; xử lý exception startup, xem nguyên nhân gốc trong log/stack trace và bảo đảm tài nguyên ngoài container được cleanup thích hợp.

## 8. Những cách sử dụng đúng và sai

| Tình huống | Đúng/sai | Lý do |
| --- | --- | --- |
| `new AnnotationConfigApplicationContext(AppConfig.class)` rồi `getBean` | Đúng | Constructor tự register + refresh |
| `new AnnotationConfigApplicationContext()`, `register(...)`, `refresh()` | Đúng | Tự hoàn tất bootstrap trước lookup |
| `new AnnotationConfigApplicationContext()`, `scan(...)`, `refresh()` | Đúng | Quét package rồi bootstrap |
| Tạo context rỗng, `register(...)` rồi lấy bean **ngay** | Sai | Chưa refresh |
| Constructor nhận class rồi gọi thêm `refresh()` | Sai | Context loại này không phải context refresh nhiều lần |
| `@Component` ở package không được scan và không đăng ký | Không đủ | Không tạo bean từ annotation đứng một mình |
| Gọi `getBean(SomeType.class)` khi chưa có bean | Lỗi lúc chạy | Thường là `NoSuchBeanDefinitionException` |
| Gọi theo type khi có nhiều candidates ngang nhau | Có thể lỗi | Thường là `NoUniqueBeanDefinitionException` |
| Tạo nhiều context, suy ra singleton được chia sẻ toàn JVM | Sai | Singleton thông thường tính theo bean definition/context |
| Không đóng context console dùng tài nguyên | Không nên | Có thể bỏ lỡ lifecycle callbacks và cleanup |

`refresh()` là điểm ranh giới quan trọng giữa **khai báo nguồn cấu hình** và **container đã khởi động**. Một số `BeanDefinition` có thể đăng ký sớm, nhưng dựa vào `getBean` trước refresh là cách sử dụng không đúng của một `ApplicationContext` chưa active.

Với một dự án web/Boot, cơ chế bootstrap và shutdown thường gắn với `SpringApplication`/server. Bài này tập trung hoàn toàn vào cách khởi tạo `AnnotationConfigApplicationContext` **thủ công**.

## 9. Sửa lỗi thường gặp khi chạy mẫu

| Hiện tượng | Nguồn gốc có thể | Cách xác minh/sửa |
| --- | --- | --- |
| `package org.springframework.context... does not exist` | Thiếu `spring-context` trên compile classpath | Kiểm POM, chạy Maven đúng thư mục, reload IDE |
| `NoSuchBeanDefinitionException` với `GreetingService` | Quên register đúng `AppConfig` hoặc chưa xử lý cấu hình | Kiểm danh sách source và gọi `refresh()` |
| `NoSuchBeanDefinitionException` với `ScanGreeting` | Quét sai package / thiếu `@Component` | Xác minh `demo.components`, tên package và annotation |
| `IllegalStateException` về chưa refresh | Tạo context rỗng rồi lookup ngay | Gọi `register`/`scan` trước và `refresh` đúng một lần |
| Exception khi refresh lần thứ hai | Đã refresh qua constructor hoặc trước đó | Không gọi lần hai; tạo context mới nếu cần đổi bootstrap |
| `NoUniqueBeanDefinitionException` | Nhiều beans phù hợp một type | Dùng `@Primary`, `@Qualifier` hoặc lookup theo bean name |
| `BeanCreationException` | Lỗi tạo bean hoặc dependency | Xem `Caused by`, constructor và factory methods |
| Java runtime thấp hơn bản build | JDK/target runtime không đồng bộ | Kiểm `java -version`, `mvn -version`, compile release |
| Maven không tải được `spring-context` | Version/repository/proxy/mirror/offline | Kiểm GAV, Maven Central và cấu hình mạng |
| Chỉ thấy output demo rồi process thoát | Console context đã đóng | Đây là hành vi đúng của demo, không nhất thiết lỗi |

**Ghi nhớ:** một lỗi `getBean` có thể là lỗi **metadata/configuration** chứ không phải lỗi “thiếu JAR”; ngược lại, lỗi `ClassNotFoundException`/compile-time package thường thuộc dependency/classpath. Phân tầng vấn đề trước khi chỉnh code.

### Công cụ hỗ trợ quan sát

- Dùng `context.containsBean("greetingService")` để hỏi bean name có được nhận biết trong context không.
- Dùng `context.getBeanDefinitionCount()` hoặc `getBeanDefinitionNames()` khi debug registration, nhưng **không ghi cứng số lượng toàn context**: Spring có thể đăng ký thêm các bean nội bộ.
- Dùng `context.getBean(GreetingService.class)` để kiểm tra kiểu cụ thể; tránh lạm dụng kiểu này bên trong mọi service vì gây service locator anti-pattern.
- Dùng logger/cause chain khi startup thất bại; đừng phụ thuộc chính xác vào chuỗi exception message vì có thể đổi theo patch.

## 10. Bài tập thực hành độc lập

Hãy làm **từng bài một**, khôi phục source gốc giữa các bài để so với stdout mục 5/6:

1. **Đổi cách bootstrap:** thay constructor `new AnnotationConfigApplicationContext(AppConfig.class)` bằng 3 dòng no-arg/`register`/`refresh`; dự đoán ba dòng `direct.*` có đổi không nếu giữ nhãn tương ứng.
2. **Bỏ `refresh()`** ở nhánh `manual`: quan sát exception khi lấy bean. Giải thích vì sao `register` không đồng nghĩa với đã khởi động context.
3. **Gọi `refresh()` hai lần** trên nhánh no-arg: thử trên bản sao, quan sát lỗi và giải thích vì sao phải dựng context mới để khởi động lại loại context này.
4. **Sai package scan:** đổi sang `demo.missing`; dự đoán `ScanGreeting` có trở thành bean không.
5. **Đổi tên bean:** đặt `@Component("welcomeComponent")` cho `ScanGreeting`, rồi sửa `containsBean(...)`; so sánh lookup theo tên và theo type.
6. **Thêm bean thứ hai cùng kiểu `Greeter`:** dự đoán ambiguous dependency ở `greetingService(Greeter)`; phân giải bằng `@Primary` hoặc `@Qualifier`.
7. **Tách config file:** chuyển `AppConfig` sang `demo.config.AppConfig` public và truyền `AppConfig.class` vào constructor; quan sát output giữ nguyên.
8. **Thêm lifecycle callback:** tạo bean có `@Bean(destroyMethod = "close")` và method `close()` in thông điệp; kiểm tra nó được gọi khi thoát try-with-resources (phân biệt với stdout chuẩn của demo gốc).
9. **So sánh singleton giữa contexts:** lấy một bean từ mỗi context rồi in `==`; giải thích vì sao cùng bean class chưa có nghĩa cùng object cho hai context.
10. **Đổi `@Lazy` hoặc `@Scope("prototype")`:** dự đoán thời điểm tạo và kết quả so reference; hiểu khác biệt với câu hỏi “context đã refresh chưa?”.

### Checklist tự đánh giá

- [ ] Phân biệt class `AnnotationConfigApplicationContext` với bean nghiệp vụ.
- [ ] Biết **hai constructor** tự `refresh` (nhận config classes/nhận package strings).
- [ ] Biết **no-arg** cần `register` hoặc `scan` rồi `refresh()`.
- [ ] Biết `@Configuration`, `@Bean`, `@Component` được đưa vào container thế nào.
- [ ] Chạy được hai main class trong cùng dự án Maven, dự đoán chính xác 6+3 dòng stdout.
- [ ] Không gọi `refresh()` lần hai trên `AnnotationConfigApplicationContext`.
- [ ] Biết đóng context và phân biệt Spring lifecycle với Java garbage collection.

## 11. Tóm tắt và tài liệu chính thức

- **Cách nhanh:** `new AnnotationConfigApplicationContext(AppConfig.class)` — config được đăng ký và context tự refresh.
- **Cách chủ động:** `new AnnotationConfigApplicationContext()` → `register(AppConfig.class)` → `refresh()`.
- **Cách quét:** `new AnnotationConfigApplicationContext()` → `scan("demo.components")` → `refresh()`.
- `getBean(...)` yêu cầu context đã bootstrap thành công; scope/lazy quyết định khi nào các instance được tạo.
- `try-with-resources` đóng context và kích hoạt lifecycle callbacks phù hợp.
- **Không có Spring Boot hay HTTP server** trong các ví dụ; tất cả đều dùng Core Container của Spring Framework.

Tài liệu tra cứu:

- [Spring Framework – Instantiating the Spring Container](https://docs.spring.io/spring-framework/reference/core/beans/java/instantiating-container.html)
- [Spring Framework – Java-based Container Configuration](https://docs.spring.io/spring-framework/reference/core/beans/java.html)
- [Spring Framework – Using @Bean](https://docs.spring.io/spring-framework/reference/core/beans/java/bean-annotation.html)
- [Spring Framework – Annotation-based Container Configuration](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config.html)
- [Spring Framework – Bean Scopes](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html)
- [Spring Framework 7.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes)

**Kết luận:** Khi tự mở một `AnnotationConfigApplicationContext`, hãy biết chắc **nguồn cấu hình đã được đăng ký hay quét chưa**, **context đã `refresh()` hay chưa**, và **ai chịu trách nhiệm đóng nó**. Nắm ba bước ấy giúp bạn xây và debug ứng dụng IoC/DI đầu tiên mà không nhầm cách khởi tạo với cách lấy bean.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="first-application-005">Lấy bean theo kiểu và thực hiện một luồng xử lý</a>

<details>
<summary>Xem chi tiết</summary>

# Lấy bean theo kiểu và thực hiện một luồng xử lý

## 1. Mục tiêu

Bài thực hành Spring Core Container này không dừng ở việc tạo `ApplicationContext`: ta lấy một bean đầu mối theo **kiểu Java** và thực sự gọi use case của nó.

Chuỗi công việc:

1. Định nghĩa một lớp nghiệp vụ `OrderService` cùng hai hợp đồng cộng tác `OrderRepository` và `Notifier`.
2. Đăng ký các bean bằng `@Configuration`/`@Bean`, để Spring nối dependency.
3. Khởi động `AnnotationConfigApplicationContext`.
4. Gọi `context.getBean(OrderService.class)` lấy bean service.
5. Gọi `service.place(request)`, theo dõi thao tác lưu, thông báo và giá trị trả về.
6. Đóng context đúng vòng đời.

~~~text
main()
  |-- ApplicationContext(AppConfig)
  |       +-- OrderRepository -> InMemoryOrderRepository
  |       +-- Notifier        -> ConsoleNotifier
  |       +-- OrderService(repository, notifier)
  |
  +-- getBean(OrderService.class)
          |
          +-- place(OrderRequest)
               +-- repository.save(...)
               +-- notifier.notifyPlaced(...) [nếu lưu mới]
               +-- trả trạng thái
~~~

**Phân biệt:** container tạo/nối bean; **lời gọi phương thức** trên bean mới thực hiện luồng nghiệp vụ. Đây là ứng dụng console, không phải HTTP endpoint, không có database transaction.

## 2. `getBean(Class<T>)` làm gì?

Ví dụ:

~~~java
OrderService service = context.getBean(OrderService.class);
~~~

Đây là **đoạn cú pháp minh họa**, giả sử `context` và `OrderService` đã được định nghĩa. `ApplicationContext` cung cấp khả năng lookup của `BeanFactory`:

- Có một bean tương thích với kiểu yêu cầu: trả về tham chiếu bean.
- Không có bean phù hợp: có thể ném `NoSuchBeanDefinitionException`.
- Nhiều bean cùng phù hợp nhưng không xác định được ứng viên duy nhất: có thể ném `NoUniqueBeanDefinitionException`.
- Bean hoặc dependency bị lỗi khi tạo: có thể gặp `BeanCreationException` hoặc lỗi injection khác, đôi khi **ngay khi context khởi động** do các singleton không lazy được tạo ở bước refresh.

`getBean` không tự quét tất cả class trên classpath rồi `new` class tùy ý. Muốn lấy được bean, phải đăng ký cấu hình thích hợp: `@Bean`, component scanning hoặc bean definition.

| API | Ý nghĩa |
| --- | --- |
| `getBean(OrderService.class)` | Tra cứu theo kiểu; thường dùng khi có một bean phù hợp |
| `getBean("orderService")` | Tra cứu theo tên; kiểu trả về là `Object` |
| `getBean("orderService", OrderService.class)` | Tra theo tên và yêu cầu đúng kiểu |
| `getBeanNamesForType(OrderService.class)` | Liệt kê các tên bean cùng kiểu để kiểm tra |
| `getBeanProvider(OrderService.class)` | Provider cho trường hợp cần lookup trì hoãn/động |

Trong ví dụ, tên bean mặc định là tên phương thức `@Bean`; tên và type là hai thứ khác nhau. Thông thường không cần lookup bằng name trong code nghiệp vụ.

## 3. Use case: tiếp nhận đơn hàng

Ta mô phỏng xử lý đơn hàng, trong đó:

- `OrderRequest` là record giữ mã đơn, mã sản phẩm và số lượng, kiểm tra điều kiện đầu vào.
- `OrderRepository` định nghĩa contract lưu và kiểm tra mã đã tồn tại.
- `Notifier` định nghĩa contract thông báo sau khi lưu thành công.
- `OrderService` chỉ biết hai contract, nhận qua constructor.
- `InMemoryOrderRepository` dùng `Map` để demo, `ConsoleNotifier` in stdout.
- `AppConfig` quyết định triển khai cụ thể và tạo các bean.
- `main` lấy `OrderService` từ context rồi gọi use case.

Quy tắc xử lý đơn giản: **mã mới thì lưu và thông báo; mã đã tồn tại thì trả trạng thái duplicate và không thông báo thêm**. Khác `OrderStore.save(...)` trong bài thiết kế hợp đồng (ném lỗi khi trùng mã), `OrderRepository.save(...)` ở bài này trả boolean để chính service quyết định nhánh `ALREADY_EXISTS`. Hai contract thể hiện hai lựa chọn nghiệp vụ khác nhau.

Hợp đồng:

~~~java
interface OrderRepository {
    boolean save(OrderRequest order);
    boolean contains(String id);
}

interface Notifier {
    void notifyPlaced(String id);
}
~~~

Khối trên chỉ là **minh họa**, chưa độc lập vì `OrderRequest` chưa có định nghĩa tại đây. Contract của `save` được quy định: trả `true` khi thêm mã mới, `false` khi mã đã có. Trong hệ thống thật có thể dùng kiểu trả về hoặc exception domain khác, nhưng mỗi implementation phải giữ cùng ý nghĩa.

| Thành phần | Trách nhiệm |
| --- | --- |
| `OrderService` | Thực hiện thứ tự `save → notify`, trả kết quả |
| `OrderRepository` | Hợp đồng lưu/tra cứu |
| `InMemoryOrderRepository` | Cách lưu bằng Map |
| `Notifier` | Hợp đồng phát thông báo |
| `ConsoleNotifier` | Cách in ra console |
| `AppConfig` | Chọn và nối concrete collaborators |
| `main` | Khởi động và lấy entry-point bean |

Dùng interface ở đây giúp giả lập repository/notifier trong unit test. **Không bắt buộc mọi dependency của Spring đều phải có interface**; một concrete class cũng có thể được inject nếu hợp lý.

## 4. Project Spring Framework 7 / Java 17 runnable

### 4.1. Chuẩn bị

Yêu cầu JDK 17+, Maven. Ví dụ dùng Spring Framework `7.0.9` qua dependency `spring-context`; không dùng Spring Boot, server, database hoặc AOP.

~~~text
first-bean-flow/
├── pom.xml
└── src/main/java/demo/FirstBeanFlow.java
~~~

Tạo `pom.xml`:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>edu.example</groupId>
    <artifactId>first-bean-flow</artifactId>
    <version>1.0-SNAPSHOT</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>7.0.9</version>
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
                    <mainClass>demo.FirstBeanFlow</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 4.2. Lớp Java đầy đủ

Tạo file `src/main/java/demo/FirstBeanFlow.java`:

~~~java
package demo;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public final class FirstBeanFlow {
    private FirstBeanFlow() {}

    public record OrderRequest(String id, String sku, int quantity) {
        public OrderRequest {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("id must not be blank");
            }
            if (sku == null || sku.isBlank()) {
                throw new IllegalArgumentException("sku must not be blank");
            }
            if (quantity <= 0) {
                throw new IllegalArgumentException("quantity must be positive");
            }
        }
    }

    public interface OrderRepository {
        boolean save(OrderRequest request);
        boolean contains(String id);
    }

    public interface Notifier {
        void notifyPlaced(String orderId);
    }

    public static final class InMemoryOrderRepository
            implements OrderRepository {
        private final Map<String, OrderRequest> orders =
                new LinkedHashMap<>();

        @Override
        public boolean save(OrderRequest request) {
            Objects.requireNonNull(request, "request");
            return orders.putIfAbsent(request.id(), request) == null;
        }

        @Override
        public boolean contains(String id) {
            return orders.containsKey(id);
        }
    }

    public static final class ConsoleNotifier implements Notifier {
        @Override
        public void notifyPlaced(String orderId) {
            System.out.println("NOTIFY " + orderId);
        }
    }

    public static final class OrderService {
        private final OrderRepository repository;
        private final Notifier notifier;

        public OrderService(OrderRepository repository, Notifier notifier) {
            this.repository = Objects.requireNonNull(repository, "repository");
            this.notifier = Objects.requireNonNull(notifier, "notifier");
        }

        public String place(OrderRequest request) {
            Objects.requireNonNull(request, "request");
            if (!repository.save(request)) {
                return "ALREADY_EXISTS " + request.id();
            }
            notifier.notifyPlaced(request.id());
            return "ACCEPTED " + request.id();
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {
        @Bean
        public OrderRepository orderRepository() {
            return new InMemoryOrderRepository();
        }

        @Bean
        public Notifier notifier() {
            return new ConsoleNotifier();
        }

        @Bean
        public OrderService orderService(
                OrderRepository repository, Notifier notifier) {
            return new OrderService(repository, notifier);
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {
            OrderService service = context.getBean(OrderService.class);

            System.out.println("SERVICE_BEANS "
                    + context.getBeanNamesForType(OrderService.class).length);
            System.out.println("SAME_SINGLETON "
                    + (service == context.getBean(OrderService.class)));

            OrderRequest request = new OrderRequest("O-101", "BOOK", 2);
            System.out.println(service.place(request));
            System.out.println(service.place(request));

            OrderRepository repository =
                    context.getBean(OrderRepository.class);
            System.out.println("STORED " + repository.contains("O-101"));
        }
    }
}
~~~

Tại thư mục `first-bean-flow`, dùng:

~~~bash
mvn -version
mvn -q compile exec:java
~~~

Maven có thể tải dependencies lần đầu. **Stdout của các lệnh `println` trong chương trình:**

~~~text
SERVICE_BEANS 1
SAME_SINGLETON true
NOTIFY O-101
ACCEPTED O-101
ALREADY_EXISTS O-101
STORED true
~~~

`mvn -version` cùng Maven/plugin có thể in thông tin phiên bản và log khác; sáu dòng là **đầu ra do code chủ động in**, không phải toàn bộ text terminal ở mọi môi trường.

### 4.3. Truy vết từng bước

1. `AnnotationConfigApplicationContext(AppConfig.class)` xử lý cấu hình, đăng ký và tạo các singleton bean không lazy khi refresh.
2. `orderRepository()` tạo repository; `notifier()` tạo notifier.
3. `orderService(OrderRepository, Notifier)` nhận bean dependency từ Spring và tạo service. Không cần `main` tự tra từng dependency trước.
4. `getBean(OrderService.class)` trả bean phù hợp. Trong demo có đúng một service nên in `SERVICE_BEANS 1`.
5. Bean service mặc định singleton trong context. Hai lần lookup lấy cùng reference, nên in `SAME_SINGLETON true`.
6. Lần gọi `place` thứ nhất: Map chưa có id, `putIfAbsent` trả `null`; service gửi một thông báo và trả `ACCEPTED O-101`.
7. Lần thứ hai: repository trả `false` do trùng id; service trả `ALREADY_EXISTS O-101` và **không gửi thông báo**.
8. `getBean(OrderRepository.class)` nhận cùng repository singleton đã inject vào service; `contains` trả `true`.
9. `try-with-resources` đóng context và cho phép Spring xử lý các callback hủy bean tương ứng.

**Giới hạn của ví dụ:** `LinkedHashMap` chỉ giữ dữ liệu trong RAM, không thread-safe, không bền vững. Không có database transaction. Nếu notifier gặp lỗi sau khi repository lưu, dữ liệu có thể đã thay đổi; Core Container không tự rollback.

## 5. Lấy bean và dependency injection là hai công việc khác nhau

`main` gọi `getBean(OrderService.class)` ở **ranh giới khởi chạy**. Đó là thao tác lookup hợp lý. Tuy nhiên, `OrderService` không tự gọi `context.getBean(OrderRepository.class)` trong phương thức `place`.

Thay vào đó, dependency rõ ràng ngay trong constructor:

~~~java
public OrderService(OrderRepository repository, Notifier notifier) {
    this.repository = Objects.requireNonNull(repository);
    this.notifier = Objects.requireNonNull(notifier);
}
~~~

Đoạn này trích từ file chạy đầy đủ. Cách làm giúp viết unit test với fake, đồng thời tránh **Service Locator**, tức giấu các phụ thuộc bằng cách cho mọi class tự giữ container rồi tra bean lúc sử dụng.

Spring tạo bean bằng `@Bean` factory method và giải quyết tham số bằng type, sau đó gọi constructor `OrderService`. **Java vẫn dùng `new` bên trong configuration**; điều được tách là quyết định tạo các collaborator ra khỏi nghiệp vụ.

Không cần `@Autowired` trên tham số `@Bean` trong cấu hình này. Cũng không cần `@ComponentScan` vì cả ba bean đã được khai báo tường minh.

## 6. Tra bean khi có nhiều ứng viên hoặc không có ứng viên

**Không tìm thấy:** nếu bỏ `@Bean orderService` và không có component registration khác, `getBean(OrderService.class)` có thể ném `NoSuchBeanDefinitionException`. Nếu thiếu một dependency của service, lỗi còn có thể xảy ra khi khởi động context vì singleton không lazy được tạo sớm.

**Nhiều ứng viên:** giả sử có hai bean implement `Notifier`. Khi `OrderService` nhận `Notifier` qua constructor, Spring có thể không xác định được nên chọn bean nào. Cần quy tắc rõ bằng `@Qualifier`, `@Primary`, hoặc cấu hình ứng viên thích hợp.

Ví dụ **minh họa, không thuộc file chạy mục 4**:

~~~java
@Bean("consoleNotifier")
Notifier consoleNotifier() {
    return new ConsoleNotifier();
}

@Bean("otherNotifier")
Notifier otherNotifier() {
    return new OtherNotifier();
}
~~~

`OtherNotifier` là tên minh họa chưa định nghĩa. Hai bean cùng interface có thể gây `NoUniqueBeanDefinitionException`. Lookup theo tên và kiểu giải quyết lựa chọn chủ động:

~~~java
Notifier chosen = context.getBean("consoleNotifier", Notifier.class);
~~~

Đừng dựa vào “bean đăng ký trước sẽ được chọn”.

**Scope:** mặc định bean singleton là một instance theo bean definition **trong một context**. Prototype thường có instance mới khi yêu cầu. Việc prototype được inject một lần vào singleton không khiến dependency tự đổi ở mọi method call.

**Proxy:** nếu sau này bật AOP hoặc `@Transactional`, bean lấy từ Spring có thể là proxy. Khi dùng JDK dynamic proxy, lookup theo interface thường phù hợp hơn concrete implementation; class-based proxy chịu giới hạn như `final` method/class. Demo này không bật AOP.

## 7. Test use case độc lập với Spring

Constructor injection cho phép tạo service bằng Java thuần, ví dụ:

~~~java
java.util.List<String> sent = new java.util.ArrayList<>();

FirstBeanFlow.OrderRepository fakeRepository =
        new FirstBeanFlow.OrderRepository() {
            public boolean save(FirstBeanFlow.OrderRequest r) {
                return true;
            }
            public boolean contains(String id) {
                return false;
            }
        };

FirstBeanFlow.Notifier fakeNotifier = sent::add;

FirstBeanFlow.OrderService service =
        new FirstBeanFlow.OrderService(fakeRepository, fakeNotifier);

String result = service.place(
        new FirstBeanFlow.OrderRequest("T-1", "PEN", 1));

if (!"ACCEPTED T-1".equals(result)
        || !sent.equals(java.util.List.of("T-1"))) {
    throw new AssertionError("Unexpected call sequence");
}
~~~

Đoạn này minh họa viết **test trong cùng project**; không phải file độc lập với `main` và không tạo thêm dòng vào stdout ở mục 4. Để test nhánh duplicate, fake repository phải trả `false` khi mã đã tồn tại và assert notifier **không** được gọi thêm.

Nên tách: unit test kiểm tra logic service bằng fake; integration test khởi động context để xác nhận wiring; test database hoặc message broker thật cho phần hạ tầng.

## 8. Xử lý lỗi và sai lầm thường gặp

| Tình huống | Điều cần kiểm tra |
| --- | --- |
| `getBean` tưởng là `new` | Bean phải được đăng ký/quản lý; lookup không tự khởi tạo class bất kỳ |
| `getBean` xong không có kết quả nghiệp vụ | Phải gọi method trên service |
| `NoSuchBeanDefinitionException` | Sai context, thiếu bean definition, profile/scanning sai |
| `NoUniqueBeanDefinitionException` | Có nhiều ứng viên; cần xác định bean rõ |
| `UnsatisfiedDependencyException` | Dependency của bean không giải quyết được |
| `BeanCreationException` | Factory/constructor/init ném lỗi |
| Bean được `new` bên ngoài và mong hưởng AOP/transaction | Instance ngoài container không tự được proxy |
| Nhầm tên bean với class name | Tên mặc định `@Bean` thường là tên phương thức |
| Cho rằng singleton là thread-safe | Scope không bảo đảm tính đồng thời của mutable Map |
| Repository lưu xong nhưng notifier lỗi | Không có rollback tự động; cần chiến lược consistency |
| Lạm dụng `context.getBean` trong mọi service | Dependency bị ẩn; ưu tiên constructor injection |

Không nên dùng exception do `getBean` để âm thầm tạo một service thủ công thay thế mà không xem xét mục tiêu quản lý bean và lifecycle.

## 9. Bài tập tự luyện

**1.** Nếu chỉ lấy `OrderService` nhưng chưa gọi `place` thì có đơn nào được lưu bởi use case không?

**Đáp án:** Không. Bean có thể được tạo trước, nhưng các thao tác `save` được viết bên trong `place`.

**2.** Vì sao có hai dòng trả kết quả nhưng chỉ một dòng `NOTIFY O-101`?

**Đáp án:** Lần đầu lưu thành công và thông báo. Lần hai id trùng, `save` trả false, service trả `ALREADY_EXISTS` ngay.

**3.** Hai lần `getBean(OrderService.class)` có luôn trả cùng reference?

**Đáp án:** Chỉ trong cấu hình singleton tương ứng của ví dụ này. Prototype có thể trả instance khác.

**4.** Mã gọi `context.getBean("orderService", OrderService.class)` có phù hợp không?

**Đáp án:** Có, vì factory method trong `AppConfig` mang tên `orderService`, trừ khi đổi cấu hình bean name.

**5.** Nếu có hai bean `Notifier` thì sửa gì?

**Đáp án:** Chỉ rõ cách chọn bằng `@Qualifier`, `@Primary` hoặc cấu hình phù hợp; tránh chọn tùy thứ tự.

**6.** Có cần Spring context để unit test `OrderService` không?

**Đáp án:** Không. Các dependency được inject qua constructor nên test có thể dùng fake thuần Java.

## 10. Checklist cuối bài

- [ ] Đã có JDK 17+, Maven và `spring-context`.
- [ ] File đặt đúng `src/main/java/demo/FirstBeanFlow.java` với package `demo`.
- [ ] `@Configuration` chứa đúng ba bean liên quan.
- [ ] Context khởi tạo được, dependency không mơ hồ.
- [ ] Lấy service bằng `getBean(OrderService.class)`.
- [ ] Gọi `service.place(request)` sau lookup.
- [ ] Hiểu thứ tự `save → notify → return` và nhánh duplicate.
- [ ] Phân biệt output của ứng dụng với log Maven.
- [ ] Đóng context khi không sử dụng nữa.
- [ ] Không suy ra `Map` là database hay Core Container tự tạo giao dịch.
- [ ] Kiểm thử nghiệp vụ bằng fake và kiểm thử wiring bằng Spring context khi cần.

**Kết luận:** `getBean(OrderService.class)` đưa mã khởi động tới entry point của **object graph đã được Spring lắp ráp**. Phần tạo và inject dependency do container tổ chức; `OrderService` thực hiện quy tắc nghiệp vụ và phối hợp repository/notifier. Phân biệt đúng các vai trò đó là bước cốt lõi của ứng dụng Spring đầu tiên.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="first-application-006">Quan sát đối tượng được tạo và phụ thuộc được cung cấp</a>

<details>
<summary>Xem chi tiết</summary>

# Quan sát đối tượng được tạo và phụ thuộc được cung cấp trong Spring Core Container

## 1. Mục tiêu: nhìn thấy quá trình IoC/DI, không chỉ học annotation

Khi một ứng dụng Java sử dụng **Spring IoC Container**, các đối tượng do container quản lý được gọi là **beans**. Container đọc cấu hình, tạo các đối tượng khi cần, tìm các dependency phù hợp và cung cấp chúng cho đối tượng phụ thuộc. **Dependency Injection (DI)** là cách đối tượng nhận dependency từ bên ngoài thay vì tự khởi tạo tất cả trong logic nghiệp vụ.

Bài này tập trung vào **bằng chứng thực nghiệm**: dùng bộ đếm constructor, danh sách sự kiện, phép so sánh cùng reference (`==`) và đầu ra CLI để trả lời:

- Bean `Repository` được tạo **khi nào** và mấy lần?
- Bean `GreetingService` nhận **đúng đối tượng Repository nào**?
- Gọi `getBean` hai lần đối với singleton có tạo thêm service không?
- Bean `prototype` có sinh instance mới mỗi lần tra cứu không?
- Bean được đánh dấu `@Lazy` đã có **định nghĩa** nhưng **chưa có instance** khi context refresh hay chưa?
- Đóng context thực hiện callback hủy ở thời điểm nào?

Không nhầm ba cấp độ: **class Java** là kiểu, **bean definition** là cấu hình container dùng để tạo/quản lý bean, còn **bean instance** là object thực đã được tạo. Có definition **không đồng nghĩa** đã có instance. Một bean definition `prototype` có thể cấp nhiều instances; bean `singleton` mặc định được chia sẻ **theo definition, trong một container**, không phải một object toàn JVM.

**Phạm vi:** Spring Framework Core Container thuần, không dùng Spring Boot, HTTP server hay database. Ví dụ dùng Java 17+, Maven, `spring-context` và `AnnotationConfigApplicationContext`. Các kết quả chính được kiểm tra bằng stdout do chính chương trình in; log nền của build tool hoặc thư viện không nằm trong expected stdout.

## 2. Đọc sơ đồ quan hệ trước khi nhìn log

Ví dụ có bốn bean:

~~~text
                  AnnotationConfigApplicationContext
                              |
                    AppConfig (@Configuration)
                              |
              +---------------+---------------+
              |               |               |
        repository        greetingService     report
        singleton         singleton          prototype
              |               |                 |
              +---inject----->|            mỗi getBean
                              |            tạo object mới
                              |
                          greet("An")
                              |
                         "data:An"

                      audit (singleton @Lazy)
                          |
              chưa cần lúc refresh;
              tạo ở lần getBean đầu
~~~

Các factory methods `@Bean` sẽ khai báo `repository`, `greetingService`, `report` và `audit`. Factory method của `greetingService` nhận một **tham số `Repository`**. Spring resolve một bean phù hợp rồi đưa **reference thật** vào lời gọi factory method, từ đó vào constructor của service.

Để quan sát đủ các góc nhìn, demo sẽ:
1. Tạo context **rỗng** rồi `register(AppConfig.class)`.
2. In các bộ đếm **trước `refresh()`**.
3. Gọi `refresh()` để bootstrap context, tạo các singleton **không lazy**.
4. In bộ đếm, creation trace và tình trạng đã đăng ký bean `audit`.
5. Lấy `GreetingService` và `Repository`; so sánh reference được inject.
6. Lấy `Report` hai lần, sau đó yêu cầu bean lazy `Audit`.
7. Đóng context bằng `try-with-resources` và quan sát destroy callback.

Chúng ta **không phụ thuộc thứ tự toàn bộ bean nội bộ của Spring** hoặc số lượng bean definitions của framework. Bộ đếm chỉ đặt trong constructor của **các lớp demo**, vì vậy không bị nhiễu bởi hạ tầng container.

## 3. Chuẩn bị dự án Maven độc lập

Cần **JDK 17+**, **Maven 3.9+**, internet lần đầu tải dependency (hoặc một mirror Maven sẵn có). Kiểm tra môi trường:

~~~bash
java -version
javac -version
mvn -version
~~~

Các lệnh kiểm tra môi trường không có stdout cố định. Nếu `mvn -version` hiển thị Java khác với `java -version`, kiểm tra `JAVA_HOME`, `PATH` và thiết lập toolchain.

Tạo cây thư mục:

~~~text
bean-observation-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── BeanObservationDemo.java
~~~

Tệp `pom.xml` đầy đủ:

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>bean-observation-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>7.0.9</version>
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
                    <mainClass>demo.BeanObservationDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

`spring-context` cung cấp `AnnotationConfigApplicationContext`, `@Configuration`, `@Bean` và kéo các module cần thiết theo dependency graph. Không cần thêm `spring-boot-starter`; Maven/Gradle tự resolve classpath. `maven.compiler.release=17` giúp biên dịch theo API Java 17.

Mốc **Spring Framework 7.0.9** cố định để ví dụ tái hiện được; nó hỗ trợ Java 17. Đối với sản phẩm thực tế, sử dụng bản vá được hỗ trợ và đồng bộ các Spring modules theo BOM/dependency management phù hợp.

## 4. Mã Java 17: ghi dấu thời điểm tạo object

Tạo đúng file `src/main/java/demo/BeanObservationDemo.java`:

~~~java
package demo;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;

public final class BeanObservationDemo {
    private BeanObservationDemo() {
    }

    // Bộ đếm và trace chỉ dùng cho chương trình demo chạy một luồng.
    private static final List<String> TRACE = new ArrayList<>();
    private static int repositoryCreated;
    private static int serviceCreated;
    private static int reportCreated;
    private static int auditCreated;

    public static final class Repository {
        private final int id;

        public Repository() {
            id = ++repositoryCreated;
            TRACE.add("repository#" + id);
        }

        public int id() {
            return id;
        }

        public String load() {
            return "data";
        }

        public void shutdown() {
            System.out.println("destroy.repository#" + id);
        }
    }

    public static final class GreetingService {
        private final Repository repository;
        private final int id;

        public GreetingService(Repository repository) {
            this.repository = repository;
            id = ++serviceCreated;
            TRACE.add("service#" + id + "(repository#" + repository.id() + ")");
        }

        public Repository repository() {
            return repository;
        }

        public String greet(String name) {
            return repository.load() + ":" + name;
        }
    }

    public static final class Report {
        private final int id;

        public Report() {
            id = ++reportCreated;
        }

        public int id() {
            return id;
        }
    }

    public static final class Audit {
        public Audit() {
            auditCreated++;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {
        @Bean(destroyMethod = "shutdown")
        public Repository repository() {
            return new Repository();
        }

        // Spring cung cấp Repository qua tham số factory method.
        @Bean
        public GreetingService greetingService(Repository repository) {
            return new GreetingService(repository);
        }

        @Bean
        @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
        public Report report() {
            return new Report();
        }

        @Bean
        @Lazy
        public Audit audit() {
            return new Audit();
        }
    }

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(AppConfig.class);

            System.out.println("beforeRefresh.repository=" + repositoryCreated);
            System.out.println("beforeRefresh.service=" + serviceCreated);

            context.refresh();

            System.out.println("afterRefresh.repository=" + repositoryCreated);
            System.out.println("afterRefresh.service=" + serviceCreated);
            System.out.println("afterRefresh.report=" + reportCreated);
            System.out.println("afterRefresh.audit=" + auditCreated);
            System.out.println("creation.trace=" + String.join(" > ", TRACE));
            System.out.println("definition.audit="
                    + context.containsBeanDefinition("audit"));

            Repository repository = context.getBean(Repository.class);
            GreetingService first = context.getBean(GreetingService.class);
            GreetingService second = context.getBean(GreetingService.class);

            System.out.println("sameService=" + (first == second));
            System.out.println("sameInjectedRepository="
                    + (first.repository() == repository));
            System.out.println("greeting=" + first.greet("An"));

            Report reportA = context.getBean(Report.class);
            Report reportB = context.getBean(Report.class);

            System.out.println("report.ids="
                    + reportA.id() + "," + reportB.id());
            System.out.println("sameReport=" + (reportA == reportB));

            Audit audit = context.getBean(Audit.class);
            System.out.println("afterLookup.audit=" + auditCreated);
            System.out.println("sameAuditSingleton="
                    + (audit == context.getBean(Audit.class)));
        }

        System.out.println("context.closed=true");
    }
}
~~~

### Tại sao cách ghi dấu này đáng tin?

- Chỉ **constructor của `Repository`** tăng `repositoryCreated` và thêm `repository#1` vào trace.
- Constructor của **`GreetingService`** nhận đúng `Repository` được truyền vào, tăng `serviceCreated` và ghi ID dependency đã nhận. Do đó trace phản ánh **thứ tự quan sát tạo hai bean nghiệp vụ**, không cần đoán thời điểm Spring tạo bean nội bộ.
- Hai bean trên là singleton **không lazy**; lúc `refresh()` hoàn thành, chúng thông thường đã được tạo. `Report` là prototype và `Audit` là lazy singleton, nên chúng chưa được tạo trong trường hợp cụ thể này.
- Lần gọi `getBean(Report.class)` nào cũng yêu cầu tạo thêm một `Report`; mỗi instance có ID tăng dần. Không dùng `System.identityHashCode`, vì giá trị đó không cố định giữa những lần chạy.
- Bean `audit` đã có **definition**, nhưng chỉ tạo instance lúc ứng dụng gọi `getBean(Audit.class)`. Nếu sau này một bean singleton eager phụ thuộc vào `Audit`, dependency đó có thể làm Audit được tạo ngay từ `refresh()`.
- `@Bean(destroyMethod="shutdown")` yêu cầu Spring gọi `Repository.shutdown()` trong quá trình đóng context. Trace này phân biệt shutdown callback với Java garbage collection.

`@Configuration(proxyBeanMethods = false)` phù hợp vì các `@Bean` methods **không gọi trực tiếp nhau**; dependency `Repository` được container cung cấp qua tham số. Nếu tự gọi `repository()` trong `greetingService()` ở chế độ này, lời gọi Java thông thường có thể tạo object khác, đi ngược điều ta muốn chứng minh.

## 5. Biên dịch, chạy và expected stdout

Mở terminal tại thư mục **`bean-observation-demo`** chứa `pom.xml`. Trên Windows PowerShell, CMD, Linux hoặc macOS, lệnh Maven sau dùng chung:

~~~bash
mvn -q clean compile exec:java
~~~

Maven Compiler Plugin sử dụng `javac` cho Java 17 và Exec Maven Plugin chạy `demo.BeanObservationDemo` với các JAR Spring/dependency transitive trên classpath. Dùng `javac BeanObservationDemo.java` trần **không đủ** nếu chưa khai báo classpath Spring.

**Stdout xác định do các lệnh `System.out.println` trong source ở mục 4 tạo ra**, không tính banner/log/diagnostics mà Maven hoặc logging backend có thể in ra:

~~~text
beforeRefresh.repository=0
beforeRefresh.service=0
afterRefresh.repository=1
afterRefresh.service=1
afterRefresh.report=0
afterRefresh.audit=0
creation.trace=repository#1 > service#1(repository#1)
definition.audit=true
sameService=true
sameInjectedRepository=true
greeting=data:An
report.ids=1,2
sameReport=false
afterLookup.audit=1
sameAuditSingleton=true
destroy.repository#1
context.closed=true
~~~

Bộ đếm `static` bắt đầu lại từ 0 khi chạy trong **JVM mới**. Nếu bạn chạy lại `main` nhiều lần trong **cùng một JVM** hoặc biến chương trình thành ứng dụng đa luồng, bộ đếm có thể khác: đây là dụng cụ quan sát cho demo một luồng, không phải một API định danh đối tượng của Spring.

Kết quả không dựa vào thời gian thực, random, địa chỉ bộ nhớ hay thứ tự các bean nội bộ của Spring. Vì `GreetingService` cần một `Repository` và trace chỉ ghi hai constructor nghiệp vụ đó, `repository#1` phải được tạo trước khi `service#1(repository#1)` hoàn tất.

## 6. Phân tích từng bằng chứng trong output

| Dòng/nhóm kết quả | Nó chứng minh điều gì? | Không nên suy diễn |
| --- | --- | --- |
| `beforeRefresh.*=0` | Mới đăng ký nguồn cấu hình, chưa khởi tạo hai bean nghiệp vụ | Không có nghĩa container chưa có bean definitions nội bộ |
| `afterRefresh.repository=1` và `service=1` | Hai singleton không lazy đã được tạo | Không có nghĩa mọi bean đều eager |
| `afterRefresh.report=0` | Chưa yêu cầu instance prototype nào | Không có nghĩa thiếu bean definition `report` |
| `afterRefresh.audit=0`, `definition.audit=true` | Metadata lazy bean đã được đăng ký, instance chưa tạo | Không có nghĩa lazy luôn thắng mọi dependency eager |
| `creation.trace=repository#1 > service#1(...)` | Trước khi constructor service hoàn thành, Spring đã cung cấp repository | Không chứng minh một thứ tự tuyệt đối giữa mọi singleton độc lập |
| `sameService=true` | Hai lookup trong cùng context trả một singleton instance | Không phải singleton toàn JVM/mọi context |
| `sameInjectedRepository=true` | Reference service giữ đúng là bean Repository được lấy từ context | Không chứng minh mọi dependency có cùng scope |
| `report.ids=1,2` và `sameReport=false` | Hai request tới prototype tạo hai đối tượng | Không tự chứng minh container sẽ hủy chúng |
| `afterLookup.audit=1` và `sameAuditSingleton=true` | Lazy singleton được tạo một lần khi yêu cầu và dùng lại | Không đồng nghĩa scope của nó là prototype |
| `destroy.repository#1` | Shutdown callback đã chạy khi đóng context | Không có nghĩa object bị GC ngay |
| `context.closed=true` | Dòng này chạy sau khi khối try-with-resources hoàn tất | Không phải phép đo số tài nguyên đã giải phóng |

### Điều được quan sát là *reference*, không chỉ giá trị

Hai object khác nhau có thể có cùng trường, cùng `toString()` hoặc `equals()` trả `true`; điều đó không có nghĩa chúng là **cùng instance**. Trong chương trình, `first == second`, `first.repository() == repository`, `reportA == reportB` kiểm tra **reference identity**.

Đừng dùng `==` để so sánh **giá trị nội dung** của `String` hay nhiều value objects thông thường; ở đây `==` được chọn có chủ đích để chứng minh bean instance nào được chia sẻ.

## 7. Phân biệt tạo bean, inject dependency và sử dụng nghiệp vụ

Quan sát ba bước liên quan nhưng không đồng nhất:

1. **Tạo object**: constructor `new Repository()` được gọi bởi factory method `@Bean repository()`. Spring tiếp nhận object mà factory method trả về làm bean theo registration.
2. **Cung cấp dependency**: container resolve bean `Repository` và đưa reference vào tham số `greetingService(Repository repository)`. Factory method truyền tiếp reference đó cho constructor của `GreetingService`.
3. **Sử dụng**: code ứng dụng lấy service bằng `getBean`, rồi gọi `first.greet("An")`. Bước gọi nghiệp vụ là Java bình thường; Spring không cần “tiêm lại” Repository mỗi lần `greet` chạy.

Một thiết kế đối lập:

~~~java
// Mã minh họa Java thuần, giả sử các type đã có trong lớp bài.
GreetingService wrong = new GreetingService(new Repository());
~~~

Dòng trên **tự tạo hai object**, không cần container resolve hay quản lý chúng. Nó có thể hợp lý trong một ứng dụng Java nhỏ, nhưng **không** chứng minh Spring đã inject một bean. Trong demo, dòng này còn làm thay đổi bộ đếm, nên đừng chèn vào chương trình gốc nếu vẫn muốn so stdout chuẩn.

### Constructor injection trong component scan

Cùng ý tưởng DI có thể được viết bằng class Spring-managed:

~~~java
// Đoạn minh họa; các class phải được đăng ký/scan và có bean Repository.
@Component
class GreetingHandler {
    private final Repository repository;

    GreetingHandler(Repository repository) {
        this.repository = repository;
    }
}
~~~

Trong một component chỉ có **một constructor**, Spring có thể chọn constructor ấy mà không cần `@Autowired` tường minh. Tuy nhiên, `@Component` **không tự** khiến class thành bean: context phải scan hoặc đăng ký class. Đoạn trên **không độc lập**; code hoàn chỉnh chạy được nằm ở mục 4.

DI giúp `GreetingService` dễ test bằng một implementation giả hoặc một dependency được truyền thủ công, nhưng không tự bảo đảm code thread-safe hay business logic đúng.

## 8. Thử thay đổi có kiểm soát để hiểu sâu hơn

Giữ bản gốc và làm từng thay đổi riêng, khôi phục code trước khi so output chuẩn.

### Thử nghiệm A — tạo bean lúc refresh hay lúc lookup?

Đổi bean `repository()` sang `@Lazy`:

~~~java
@Bean
@Lazy
public Repository repository() {
    return new Repository();
}
~~~

Dù `Repository` có `@Lazy`, bean `GreetingService` vẫn **singleton eager** và bắt buộc cần `Repository`. Do đó repository có thể vẫn được tạo trong `refresh()`: lazy singleton **không trì hoãn dependency bắt buộc của singleton eager**. Đây là tình huống phản ví dụ quan trọng; không nên dự đoán `afterRefresh.repository=0` chỉ dựa trên annotation mới.

Nếu đồng thời đánh dấu **`GreetingService` là `@Lazy`** và không có bean eager nào phụ thuộc hai bean này, cả hai có thể chưa được tạo lúc refresh. Lần đầu lấy service sẽ khiến Spring resolve và tạo repository rồi service.

### Thử nghiệm B — singleton và prototype

Đổi `@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)` trên `report()` thành singleton mặc định. Nếu không có bean eager nào dùng report, context thông thường sẽ pre-instantiate singleton này trong refresh, nên `afterRefresh.report` có thể thành `1`, `report.ids` thành `1,1` và `sameReport=true`.

Nếu giữ report prototype mà inject nó **một lần vào constructor của một singleton**, singleton thường giữ **cùng instance prototype đã inject** suốt đời của nó; Spring không tự làm mới dependency mỗi lần bạn gọi phương thức singleton. Muốn tạo mới theo nhu cầu, có thể xem `ObjectProvider<Report>` hoặc lookup-method injection — đó là chủ đề nâng cao, không phải chức năng mặc định của field injection.

### Thử nghiệm C — đổi thứ tự registration có làm DI hỏng?

Thử đổi vị trí phương thức `repository()` và `greetingService()` trong `AppConfig`. Bean graph vẫn có thông tin dependency kiểu `Repository`; Spring không dựa vào người viết source đặt factory method ở đầu file để quyết định service nhận dependency nào. **Đừng đặt yêu cầu về thứ tự tuyệt đối của các singleton độc lập** dựa vào thứ tự code trong file.

### Thử nghiệm D — giả lập thiếu dependency

Tạm xóa hoặc comment `@Bean repository()` trên bản sao rồi chạy lại. `GreetingService` còn yêu cầu `Repository`, nên context có thể thất bại khi bootstrap với exception dạng `UnsatisfiedDependencyException`/`NoSuchBeanDefinitionException` lồng nhau. Thông điệp chi tiết và thứ tự cause có thể thay đổi theo phiên bản; hãy quan sát **nguyên nhân gốc** thay vì chép một stack trace cố định.

### Thử nghiệm E — dùng hai container

Tạo context thứ hai với cùng `AppConfig`. Singleton được chia sẻ **trong từng context**, không mặc nhiên qua hai contexts. Nếu giữ bộ đếm `static` của demo, IDs tiếp tục tăng trong cùng JVM; đó là **bộ đếm do ta viết**, không phải Spring tự gán ID global cho bean.

## 9. Cách quan sát thực tế: logger, debugger và kiểm thử

Bộ đếm và `System.out.println` có giá trị khi học, nhưng dự án thực tế nên quan sát ít xâm lấn và kiểm thử có mục tiêu:

- **Debugger**: đặt breakpoint ở constructor `Repository`, `GreetingService`, `report()` hoặc `audit()`. Xem call stack, đối số `repository`, reference identity và nhánh dẫn tới việc tạo bean.
- **Logger**: ghi sự kiện lifecycle có cấu trúc khi thực sự cần; không log secret hoặc dữ liệu nhạy cảm. Tránh phụ thuộc thứ tự startup logger framework cho test logic.
- **Test identity**: dùng assertions để kiểm chứng `assertSame` / `assertNotSame` tương đương với mục đích tham chiếu. Nếu dùng JUnit, hãy thêm dependency và cấu hình build tương ứng; demo hiện tại **không yêu cầu JUnit**.
- **`getBeanDefinitionNames()`**: dùng để điều tra metadata đang đăng ký, nhưng con số và thứ tự liệt kê có thể gồm bean nội bộ và không ổn định. Không dùng “bean count toàn context” làm expected output của bài.
- **`BeanPostProcessor`**: có thể quan sát/gắn xử lý trước–sau initialization ở cấp bean. Đó là extension point nâng cao, **khác** với `BeanFactoryPostProcessor` vốn tập trung vào metadata.
- **Actuator**: dành cho ứng dụng Spring Boot khi đã thêm dependency/cấu hình cần thiết. Demo Framework thuần không có Actuator mặc định.

**Không đo tốc độ khởi động bằng số dòng constructor log.** Một ứng dụng có lazy beans, proxy hoặc nhiều scopes vẫn có thể khởi tạo đúng theo thiết kế. “Bean đã đăng ký”, “bean instance đã tạo”, “dependency đã inject” và “đã chạy logic nghiệp vụ” là bốn quan sát riêng.

## 10. Lỗi thường gặp khi đọc kết quả

| Nhận định/lỗi | Sự thật hoặc cách kiểm tra |
| --- | --- |
| “Gọi `getBean` thì Spring mới tạo mọi singleton” | Singleton không lazy thường được tạo trong `ApplicationContext.refresh()` |
| “Có `BeanDefinition` thì constructor chắc chắn đã chạy” | Metadata có thể tồn tại trước instance, đặc biệt lazy/prototype |
| “`@Lazy` luôn ngăn bean tạo lúc startup” | Eager singleton cần nó vẫn có thể kích hoạt việc tạo |
| “Một class Java chỉ có một object trong Spring” | Nhiều definitions/contexts/scopes có thể có nhiều instances cùng type |
| “Prototype được inject vào singleton thì tự tạo mới ở mỗi method call” | Inject thường xảy ra một lần lúc tạo singleton |
| “Hai bean có giá trị bằng nhau là cùng reference” | Muốn kiểm tra identity phải xét reference/quan hệ `==` đúng ngữ cảnh |
| “Một object do code `new` là Spring bean” | Chỉ thành bean nếu đi qua cơ chế container quản lý/đăng ký thích hợp |
| “`@Bean` factory method là một instance” | Method là nguồn khai báo cách tạo; object trả về mới là bean instance |
| “`close()` khiến object mất khỏi heap ngay” | Context gọi destruction callbacks phù hợp; JVM GC độc lập |
| “Nhìn log tạo service là DI thành công hoàn toàn” | Cần xác nhận dependency đúng reference/type và service hoạt động |
| “Mọi bean luôn tạo theo thứ tự khai báo trong file” | Dependency graph, scope, lazy và processing quyết định; đừng dựa vào thứ tự source |
| “Lỗi `NoSuchBeanDefinitionException` do Maven thiếu Spring JAR” | Thường là thiếu bean phù hợp trong context; phân biệt với compile/classpath error |

### Khi output thực tế lệch

1. **Kiểm tra code đã sửa chưa**: `@Lazy`, `@Scope`, factory method, lần gọi `getBean` có khác bản gốc không?
2. **Kiểm tra chạy trong JVM mới hay cùng JVM**: bộ đếm `static` có thể giữ state nếu chạy lại `main` trong cùng JVM.
3. **Tách log khỏi stdout ứng dụng**: Maven, Spring, logging backend có thể tạo thêm diagnostic.
4. **Đọc exception chain**: nếu thiếu dependency hoặc có nhiều bean cùng type, context có thể thất bại ngay ở `refresh()` trước khi in phần after-refresh.
5. **Kiểm tra Java và dependencies**: `mvn -version`, `mvn dependency:tree` để phát hiện runtime/JDK/version conflict.
6. **Đối chiếu bằng identity**: nếu `sameInjectedRepository=false`, kiểm tra có tạo thêm object bằng `new`, cấu hình nhiều definitions, proxy hoặc scope ngoài dự kiến hay không.

## 11. Bài tập tự kiểm tra và tài liệu chính thức

Làm từng bài trên một bản sao sạch, giải thích dự đoán trước khi chạy:

1. Chỉ ra hai dòng chứng minh **Repository được tạo trước Service** và một dòng chứng minh Service nhận cùng bean Repository từ context.
2. Giải thích vì sao `reportCreated=0` sau refresh nhưng `getBean(Report.class)` hai lần vẫn tạo ID `1,2`.
3. Dự đoán `auditCreated` nếu không gọi `getBean(Audit.class)` ở bất kỳ nơi nào.
4. Đổi `Report` sang singleton; ghi nhận chính xác những dòng expected stdout thay đổi.
5. Gắn `@Lazy` cho Repository rồi giải thích vì sao vẫn có thể được tạo lúc refresh do Service.
6. Thêm một bean `GreetingService` khác có type giống nhau; dự đoán hành vi lookup bằng type và cách sửa bằng `@Primary`/`@Qualifier`.
7. Thử `new Repository()` thủ công và quan sát counter tăng nhưng bean `repository` trong Spring **không bị thay thế**.
8. Giữ một reference tới Repository sau `context.close()`. Giải thích shutdown callback khác garbage collection ra sao.
9. Bỏ definition Repository trên bản sao, tìm nguyên nhân exception ở bước refresh; không coi exception message chi tiết là API ổn định.
10. Chạy test identity mà **không cần Spring**, tự truyền Repository vào constructor GreetingService; chỉ ra điểm giống và khác với DI do container tự resolve.

**Checklist đạt bài:**

- [ ] Phân biệt thời điểm `register`, `refresh`, tạo singleton, lấy prototype, lấy lazy bean và `close()`.
- [ ] Biết **factory-method parameter injection** truyền bean instance nào cho service.
- [ ] Hiểu `sameService=true` và `sameInjectedRepository=true` kiểm tra hai quan hệ khác nhau.
- [ ] Nêu rõ tại sao **bean definition** không đồng nghĩa **đã tạo object**.
- [ ] Không nhầm singleton theo container với singleton toàn JVM.
- [ ] Tự biên dịch/chạy Maven Java 17 và dự đoán chính xác 17 dòng stdout gốc.
- [ ] Phân biệt logging quan sát và side effects quan sát do demo tự thêm.

**Tài liệu chính thức:**

- [Spring Framework – IoC Container](https://docs.spring.io/spring-framework/reference/core/beans.html)
- [Spring – Bean Scopes](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html)
- [Spring – Lazy-initialized Beans](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-lazy-init.html)
- [Spring – Java-based Container Configuration](https://docs.spring.io/spring-framework/reference/core/beans/java.html)
- [Spring – Using the @Bean Annotation](https://docs.spring.io/spring-framework/reference/core/beans/java/bean-annotation.html)
- [Spring – Dependency Injection](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html)
- [Spring Framework 7.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes)

**Kết luận:** Muốn biết Spring đã làm gì, hãy quan sát **đúng ranh giới**: metadata được đăng ký, constructor chạy, dependency reference được inject, bean được lấy và callback đóng được gọi. Trong demo, các bộ đếm, nhật ký tạo và phép so reference cho bằng chứng cụ thể mà không phải dựa vào cảm giác “Spring đã tự tạo tất cả”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="first-application-007">Đóng context bằng try-with-resources</a>

<details>
<summary>Xem chi tiết</summary>

# Đóng Spring ApplicationContext bằng try-with-resources — Spring Core Container

## 1. Mục tiêu và tình huống sử dụng

Bạn đã biết tạo Spring IoC container để đăng ký và lấy bean. Bước tiếp theo là **kết thúc vòng đời container đúng cách**. Một ứng dụng Java chạy một tác vụ rồi thoát có thể tự tạo `AnnotationConfigApplicationContext`; khi hoàn tất, nó phải **đóng context** để Spring có cơ hội gọi các callback hủy bean, giải phóng tài nguyên do bean quản lý và đánh dấu context không còn hoạt động.

**Try-with-resources** là cấu trúc Java thích hợp khi chính đoạn code của bạn **sở hữu vòng đời context**: đóng context theo phạm vi khối lệnh thay vì phải nhớ gọi `close()` ở nhiều đường thoát.

Kết thúc bài học, bạn có thể:

- Phân biệt **đóng Spring context** với chỉ rời phương thức `main`, bỏ reference hoặc để JVM tự thoát.
- Dùng cú pháp `try (AnnotationConfigApplicationContext context = ...)` và `try (context)` trong Java 17.
- Giải thích vì sao Spring context đóng được nhờ giao diện `AutoCloseable`/`Closeable`, và `close()` xảy ra cả khi thân `try` thoát bằng exception.
- Chạy ứng dụng Spring Framework thuần với bean có phương thức hủy `@Bean(destroyMethod = "shutdown")` và **quan sát thứ tự stdout**.
- Đóng context kể cả khi phần `register(...)` / `refresh()` được thực hiện bên trong khối và có khả năng thất bại.
- Nhận diện giới hạn: `prototype` không tự được Spring hủy trọn đời, object tạo ngoài container không tự được quản lý, `System.exit`/crash không phải đường dọn tài nguyên bình thường.
- Biết khi nào **không được đóng** context vì lifecycle thuộc Spring Boot, Spring Test, framework host hoặc thành phần khác.

**Phạm vi:** Bài tập trung vào lifecycle và `try-with-resources` của **Spring Core Container**. Không cần Spring Boot, HTTP server, database, transactional API hay cơ chế shutdown phức tạp để học kỹ năng này.

## 2. Ai sở hữu context thì chịu trách nhiệm đóng?

Một ví dụ dễ gặp:

~~~java
AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(AppConfig.class);
GreetingService service = context.getBean(GreetingService.class);
System.out.println(service.greet("Lan"));
// Nếu kết thúc mà không đóng, container không được yêu cầu dọn dẹp có chủ đích.
~~~

**Đoạn trên chỉ minh họa**, chưa gồm `AppConfig`, `GreetingService` và file Java hoàn chỉnh. Khi ứng dụng sở hữu đối tượng context, nên bảo đảm có `context.close()`.

Tại sao không chỉ chờ garbage collector?

- Garbage collector xử lý **bộ nhớ Java đủ điều kiện thu gom**, không có trách nhiệm gọi callback hủy của bean.
- Bean có thể giữ **connection pool, file handle, background executor, socket, cache hoặc thread**; một số tài nguyên cần shutdown có trật tự để dừng công việc/flush dữ liệu.
- Java không bảo đảm `finalize()` là cơ chế quản lý tài nguyên an toàn; không dùng finalization thay `close()`.
- Ứng dụng short-lived có thể kết thúc trước khi bất cứ cơ chế dọn rác nào có ích; ứng dụng chạy lâu có thể giữ tài nguyên do reference còn tồn tại.

**Phân biệt hai loại sở hữu:**

| Context được ai tạo/quản lý? | Ai quyết định đóng? |
| --- | --- |
| Chính code Java khởi tạo `new AnnotationConfigApplicationContext()` để thực hiện một tác vụ | Chính code đó, thường qua `try-with-resources` |
| Một library tạo context nội bộ và trả service cho bạn | Theo hợp đồng của library; không tùy tiện đóng context của library |
| Spring Boot/framework host tạo ApplicationContext chính | Framework/lifecycle chủ sở hữu thường quyết định shutdown; không đóng trong một service xử lý request |
| Spring Test quản lý và cache test context | Test infrastructure quản lý; không tự đóng sau từng test nếu framework cần dùng lại |

Nếu một service nhận `ApplicationContext` từ nơi khác và gọi `close()`, nó có thể **tắt cả ứng dụng** hoặc phá những bean mà thành phần khác còn cần. Đừng biến việc đóng context thành trách nhiệm của bean nghiệp vụ tùy tiện.

## 3. Hai kiểu try-with-resources hợp lệ trong Java 17

### 3.1. Khai báo resource ngay trong ngoặc try

~~~java
try (AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(AppConfig.class)) {

    GreetingService service = context.getBean(GreetingService.class);
    System.out.println(service.greet("Lan"));
} // Java gọi context.close() khi rời khối theo đường bình thường.
~~~

**Đây là snippet**, còn thiếu import và các lớp nghiệp vụ. `AnnotationConfigApplicationContext` thuộc Spring Framework và đáp ứng hợp đồng `AutoCloseable` thông qua các giao diện context. Java tự gọi `close()` khi rời khối, kể cả khi code thân `try` ném exception.

**Cảnh báo khi bootstrap:** constructor `new AnnotationConfigApplicationContext(AppConfig.class)` thông thường tiến hành đăng ký và refresh ngay. Nếu construction/refresh **ném exception trước khi object được gán thành công vào resource của try**, resource đó chưa được thiết lập trong try-with-resources; không nên tuyên bố khối này luôn đóng được mọi đối tượng context mới tạo thất bại nửa chừng.

### 3.2. Resource là biến local đã khai báo trước: try(context)

Từ Java 9 (bao gồm Java 17), có thể viết:

~~~java
AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext();

try (context) {
    context.register(AppConfig.class);
    context.refresh();
    GreetingService service = context.getBean(GreetingService.class);
    System.out.println(service.greet("Lan"));
}

System.out.println("active=" + context.isActive());
~~~

Đây cũng là snippet; chương trình hoàn chỉnh ở mục 5.

- Biến `context` phải **final hoặc effectively final** (không được gán lại sau khởi tạo) để dùng dạng `try (context)`.
- Với constructor **không tham số**, context được tạo nhưng **chưa refresh**. Đưa `register(AppConfig.class)` và `refresh()` **vào bên trong khối** để dù refresh thất bại, resource đã nằm trong vùng mà try-with-resources sẽ cố đóng.
- Khi chạy thành công và rời khối, `close()` đã được gọi. `isActive()` sau đó trả `false` trong ví dụ `AnnotationConfigApplicationContext`.
- Biến `context` vẫn tồn tại sau khối chỉ vì nó được khai báo ngoài; **đừng tiếp tục dùng nó để truy xuất bean**, vì context đã đóng.

### 3.3. So sánh với try-finally

Cách Java cũ thường viết:

~~~java
AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext();
try {
    context.register(AppConfig.class);
    context.refresh();
    // Sử dụng bean.
} finally {
    context.close();
}
~~~

Nếu đã khởi tạo context thành công, cách này cũng có ý định dọn dẹp. Nhưng `try-with-resources` ngắn gọn hơn, thể hiện **phạm vi sở hữu resource**, xử lý cơ chế `close()` chuẩn của Java và có semantics chính thức khi cả thân và việc đóng resource ném exception.

**Một lần đóng có chủ đích** là đủ. Không thêm `context.close()` vào trong khối `try (context)` chỉ để “chắc ăn”; dễ làm khó đọc và khiến người học nhầm thứ tự lifecycle.

## 4. Điều gì xảy ra khi context đóng?

Đối với `AnnotationConfigApplicationContext` đã refresh thành công, `close()` đi qua quy trình đóng context của Spring. Trong điều kiện bình thường, Spring:

1. Phát tín hiệu và thực hiện các giai đoạn shutdown/lifecycle thích hợp cho context.
2. Dừng các bean/lifecycle components thuộc cơ chế lifecycle khi có cấu hình tương ứng.
3. Thực hiện các callback hủy trên **các singleton được quản lý** có khai báo/cơ chế hủy thích hợp (ví dụ method qua `@Bean(destroyMethod = "...")`, `DisposableBean`, `@PreDestroy` khi có hỗ trợ annotation).
4. Giải phóng các tài nguyên nội bộ liên quan tới vòng đời context và đánh dấu context không active nữa.

Không phải **mọi bean** đều có destroy callback. Một bean chỉ chứa các giá trị immutable thường không cần làm gì khi đóng. Container không tự biết đóng một resource được tạo ngoài context rồi lưu ở biến global.

### Đóng context không đồng nghĩa đóng từng đối tượng bằng tay

Trong demo sẽ có một `ManagedAudit` bean với method:

~~~java
public void shutdown() {
    System.out.println("destroy=ManagedAudit");
}
~~~

Là snippet trong class, method này tự nó chỉ là Java. Khi factory method mang annotation:

~~~java
@Bean(destroyMethod = "shutdown")
public ManagedAudit managedAudit() {
    return new ManagedAudit();
}
~~~

Spring sẽ đăng ký **destroy callback** cho bean được quản lý đó. Khi đóng context có bean singleton đã tạo, Spring gọi `shutdown()`. Bạn **không cần** lấy bean rồi tự gọi `shutdown()` trong `main`.

**Đừng lẫn `@Bean(destroyMethod=...)` với `@PreDestroy`**: cả hai có thể dùng để chỉ định công việc hủy trong những điều kiện hỗ trợ, nhưng `@PreDestroy` cần annotation Jakarta phù hợp trong Spring 6+, còn demo này dùng cách rõ ràng `destroyMethod="shutdown"` và không phụ thuộc annotation lifecycle ngoài module đang dùng.

## 5. Project hoàn chỉnh: nhìn thấy callback hủy trên stdout

Dùng **JDK 17+, Maven, Spring Framework 7.0.9** (module `spring-context`). Đây là phiên bản ví dụ được cố định để lệnh và dependency tái lập, **không phải lời khẳng định bản Spring mới nhất**. Không cần Spring Boot, database hay HTTP server.

Cấu trúc thư mục:

~~~text
context-close-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── ContextCloseDemo.java
~~~

### 5.1. File pom.xml

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>context-close-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>7.0.9</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.14.1</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.1</version>
                <configuration>
                    <mainClass>demo.ContextCloseDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Maven lấy `spring-context` và những dependency transitively cần thiết. Chạy `javac` thủ công cần classpath tương ứng; chỉ đưa một JAR Spring vào là thường chưa đủ.

### 5.2. File src/main/java/demo/ContextCloseDemo.java

~~~java
package demo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public final class ContextCloseDemo {
    private ContextCloseDemo() {}

    public interface GreetingRepository {
        String message(String name);
    }

    public static final class SimpleGreetingRepository
            implements GreetingRepository {
        @Override
        public String message(String name) {
            return "Xin chao, " + name + "!";
        }
    }

    public static final class ManagedAudit {
        private final List<String> events = new ArrayList<>();

        public void record(String name) {
            events.add(name);
        }

        public List<String> snapshot() {
            return List.copyOf(events);
        }

        public void shutdown() {
            System.out.println("destroy=ManagedAudit");
        }
    }

    public static final class GreetingService {
        private final GreetingRepository repository;
        private final ManagedAudit audit;

        public GreetingService(
                GreetingRepository repository, ManagedAudit audit) {
            this.repository = Objects.requireNonNull(repository);
            this.audit = Objects.requireNonNull(audit);
        }

        public String greet(String input) {
            if (input == null || input.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
            String name = input.strip();
            String message = repository.message(name);
            audit.record(name);
            return message;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {
        @Bean
        public GreetingRepository greetingRepository() {
            return new SimpleGreetingRepository();
        }

        @Bean(destroyMethod = "shutdown")
        public ManagedAudit managedAudit() {
            return new ManagedAudit();
        }

        @Bean
        public GreetingService greetingService(
                GreetingRepository repository, ManagedAudit audit) {
            return new GreetingService(repository, audit);
        }
    }

    public static void main(String[] args) {
        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext();

        try (context) {
            context.register(AppConfig.class);
            context.refresh();

            GreetingService service = context.getBean(GreetingService.class);
            ManagedAudit audit = context.getBean(ManagedAudit.class);

            System.out.println("insideActive=" + context.isActive());
            System.out.println("singleton=" +
                    (service == context.getBean(GreetingService.class)));
            System.out.println("greeting=" + service.greet(" Lan "));
            System.out.println("audit=" + audit.snapshot());
        }

        System.out.println("afterActive=" + context.isActive());
    }
}
~~~

**Điểm cần hiểu trước khi chạy:**

- `new AnnotationConfigApplicationContext()` tạo context chưa được refresh; `register(AppConfig.class)` và `refresh()` nằm trong `try (context)`.
- Bean `greetingRepository` cung cấp `GreetingRepository`. Bean `managedAudit` được đánh dấu để gọi method `shutdown()` lúc Spring hủy singleton.
- Bean `greetingService` nhận repository và audit qua tham số factory method. Service **không trực tiếp tra ApplicationContext**, không tự quản lý lifecycle audit.
- `ManagedAudit` chỉ là object Java có method `shutdown()`; Spring biết gọi method ấy nhờ metadata `@Bean(destroyMethod="shutdown")`.
- Trong `try`, service ghi một tên đã chuẩn hóa vào audit; tại cuối khối, **Java gọi context.close()**, và Spring gọi callback hủy audit **trước khi** code ngoài khối in `afterActive=false`.
- Để output có thứ tự xác định, chỉ một bean phát thông báo trong destroy callback. Bài không dựa vào thứ tự khởi tạo của các singleton độc lập.

### 5.3. Chạy bằng Maven

Từ thư mục `context-close-demo`:

~~~bash
java -version
javac -version
mvn -version
mvn -q clean compile exec:java
~~~

**Stdout chính xác do chương trình Java in khi chạy thành công:**

~~~text
insideActive=true
singleton=true
greeting=Xin chao, Lan!
audit=[Lan]
destroy=ManagedAudit
afterActive=false
~~~

Sáu dòng trên là output từ các lệnh `System.out.println(...)` trong mã demo. Các dòng version của JDK/Maven và logging của tool/framework (nếu cấu hình có) **không tính** là output do đoạn mã demo tự in. Spring thực hiện destroy callback khi context đóng; `destroy=ManagedAudit` **không** có nghĩa Java tự động gọi `shutdown()` trên mọi object có method cùng tên.

### 5.4. Tự dùng javac/java

Vì import Spring API, phải biên dịch/chạy với Spring JAR và dependency transitively liên quan. Nhờ Maven tạo file classpath trước:

~~~bash
mvn -q dependency:build-classpath -Dmdep.outputFile=classpath.txt
~~~

**Linux/macOS (bash/zsh):**

~~~bash
mkdir -p out
javac --release 17 -encoding UTF-8 \
  -cp "$(cat classpath.txt)" \
  -d out src/main/java/demo/ContextCloseDemo.java
java -cp "out:$(cat classpath.txt)" demo.ContextCloseDemo
~~~

**Windows PowerShell:**

~~~powershell
$deps = (Get-Content -Raw classpath.txt).Trim()
New-Item -ItemType Directory -Force out | Out-Null
javac --release 17 -encoding UTF-8 -cp "$deps" -d out src/main/java/demo/ContextCloseDemo.java
java -cp "out;$deps" demo.ContextCloseDemo
~~~

Dấu phân tách classpath là `:` trên Linux/macOS và `;` trên Windows. Cả hai đường chạy gọi cùng `main`, do đó sáu dòng stdout của code không thay đổi.

## 6. Dòng thời gian: try-with-resources và Spring destroy callback

~~~mermaid
sequenceDiagram
    participant M as main()
    participant J as Java try-with-resources
    participant C as ApplicationContext
    participant S as GreetingService
    participant A as ManagedAudit
    M->>J: try (context)
    J->>C: register(AppConfig) / refresh()
    C->>S: tạo bean + inject dependencies
    M->>S: greet(" Lan ")
    S->>A: record("Lan")
    M-->>J: rời thân try
    J->>C: close()
    C->>A: shutdown() (destroy callback)
    A-->>M: println destroy=ManagedAudit
    M->>C: isActive()
    C-->>M: false
~~~

Đây là mô hình những bước liên quan trực tiếp tới ví dụ, **không mô tả tất cả Spring lifecycle internals**. Java chịu trách nhiệm gọi `close()` theo cấu trúc `try-with-resources`; Spring chịu trách nhiệm diễn giải việc đóng context thành callback trên các bean mà nó quản lý.

Để tránh nhầm:

- `GreetingService.greet(...)` chạy **vì `main` gọi nó**, không phải vì container tạo bean.
- `ManagedAudit.shutdown()` chạy **vì context đóng và bean có destroy metadata**, không phải vì kết thúc scope Java của biến `audit`.
- `context.isActive()` là **trạng thái context**, không phải kiểm tra mỗi bean còn object trong bộ nhớ. Reference tới object có thể vẫn tồn tại sau khi context đóng, nhưng dùng các collaborator đã shutdown là không an toàn hoặc sai hợp đồng.

## 7. Khi có exception: Java bảo đảm gì?

Try-with-resources có mục tiêu giúp resource được **đóng khi rời khối theo cơ chế ngôn ngữ**, kể cả khi thân `try` thất bại:

~~~java
AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext();

try (context) {
    context.register(AppConfig.class);
    context.refresh();
    throw new IllegalStateException("example failure");
} // Java vẫn gọi context.close() khi thoát do exception.
~~~

Đây chỉ là **snippet minh họa** để quan sát semantics Java; nó sẽ ném exception và không tạo stdout “happy path”. Không ghép trực tiếp vào chương trình chính nếu đang so sánh sáu dòng đầu ra.

Nếu cần bắt lỗi ứng dụng tại ranh giới, dùng cấu trúc:

~~~java
AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext();

try (context) {
    context.register(AppConfig.class);
    context.refresh();
    // Sử dụng bean; có thể ném lỗi.
} catch (RuntimeException failure) {
    System.err.println("Job failed: " + failure.getClass().getSimpleName());
}
~~~

Trong Java, **resource được đóng trước khi phần catch tương ứng ở ngoài xử lý exception**. Nhưng việc bạn catch rồi tiếp tục có hợp lý hay không phụ thuộc nghiệp vụ; đừng nuốt lỗi bootstrap Spring rồi tiếp tục chạy với service null.

### Suppressed exception là quy tắc Java, không phải cam kết của mọi destroy callback Spring

Nếu **thân try** ném exception A và thao tác **close()** của resource cũng ném exception B ra ngoài, Java giữ A làm exception chính, B được gắn dưới `A.getSuppressed()`. Đây là **cơ chế chung của Java try-with-resources**.

Trong Spring, exception từ bean destroy callback có thể được framework ghi log/xử lý theo đường nội bộ; **không được suy ra** tất cả lỗi shutdown của từng bean nhất định sẽ xuất hiện trong `getSuppressed()` của exception thân try. Khi debug đóng context, cần xem logging/callback cụ thể.

**Giới hạn quan trọng:** try-with-resources không thể bảo đảm callback được chạy khi cả JVM/process bị chấm dứt đột ngột, `Runtime.halt(...)`, máy mất điện hoặc crash. Bảo đảm trong ngữ nghĩa Java áp dụng cho **luồng điều khiển thông thường hoặc exception** khi thực sự rời khối và resource đã được đăng ký với try.

## 8. Đặt refresh() bên trong khối để quản lý lỗi bootstrap

So sánh hai cách:

**Cách ngắn cho ví dụ rất nhỏ:**

~~~java
try (AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(AppConfig.class)) {
    // Context đã refresh khi constructor trả về thành công.
}
~~~

**Cách cho phép đưa giai đoạn khởi động vào phạm vi dọn dẹp:**

~~~java
try (AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext()) {
    context.register(AppConfig.class);
    context.refresh();
    // Chạy tác vụ.
}
~~~

Nếu quá trình `refresh()` trong thân `try` ném lỗi, resource context đã được tạo và gắn vào cơ chế try-with-resources, nên Java vẫn gọi `close()` khi thoát. Điều này **không có nghĩa bean khởi tạo thất bại chắc chắn đã tạo xong hoặc có đầy đủ destroy callback**; Spring có các pha cleanup riêng khi refresh thất bại.

Dạng thứ hai cũng giúp việc thêm cấu hình động rõ ràng:

~~~java
try (AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext()) {
    context.register(AppConfig.class);
    context.refresh();
    System.out.println(context.containsBean("greetingService"));
}
~~~

Đây là snippet chạy trong một chương trình có `AppConfig`, **không phải file độc lập**. Không gọi `refresh()` lặp nhiều lần trên cùng `GenericApplicationContext/AnnotationConfigApplicationContext`; các context thuộc nhóm này có chính sách refresh một lần.

## 9. Scope và giới hạn của quản lý destroy

| Trường hợp | Hành vi cần nhớ |
| --- | --- |
| Managed singleton đã được tạo, có destroy callback | Context thường gọi callback khi đóng thành công |
| Bean không có callback hay tài nguyên riêng | Không cần in thông báo hoặc làm việc hủy đặc biệt |
| Bean prototype được Spring tạo và giao cho caller | Spring thường **không quản lý toàn bộ bước hủy prototype** sau đó; caller chịu trách nhiệm phù hợp |
| Object tự tạo bằng `new` ngoài container | Không tự nhận destroy callback của Spring |
| Resource được bean mở nhưng không được bean đóng đúng | Đóng context không tự khắc phục lỗi quản lý resource trong mã bean |
| Context bị framework khác sở hữu | Không tùy tiện dùng try-with-resources để đóng context mà thành phần khác còn sử dụng |

### Prototype là ngoại lệ quan trọng

~~~java
@Bean
@org.springframework.context.annotation.Scope("prototype")
SomeWorker someWorker() {
    return new SomeWorker();
}
~~~

Đây chỉ là snippet minh họa, `SomeWorker` không được định nghĩa trong ví dụ này. Spring tạo prototype khi có yêu cầu nhưng **không tự chạy toàn bộ lifecycle destruction** của prototype sau khi giao object cho caller. Nếu `SomeWorker` sở hữu file/socket, code sở hữu object hoặc cơ chế quản lý tài nguyên riêng phải quan tâm đóng.

Đừng đọc câu “đóng context sẽ đóng tất cả bean” theo nghĩa bao trùm mọi loại scope và mọi object do Java tạo ra.

### Có nên dùng @PreDestroy thay @Bean(destroyMethod)?

Có thể, nếu bean được container quản lý và application có API annotation phù hợp. Với Spring Framework 6, annotation tiêu chuẩn là `jakarta.annotation.PreDestroy`, **không phải `javax.annotation.PreDestroy`** của ứng dụng cũ. Khi dùng cần dependency/annotation processing tương ứng theo môi trường.

Trong bài này, `@Bean(destroyMethod="shutdown")` là lựa chọn đơn giản nhất để thấy trực tiếp cách định nghĩa callback ở cấu hình Java, không cần thêm thư viện annotation.

Bean có thể implement `DisposableBean` hoặc một interface Java như `AutoCloseable`; Spring cũng có thể tự suy luận một số destroy method `close`/`shutdown` trong những tình huống phù hợp. Tuy nhiên, **không dựa vào suy luận mơ hồ cho ví dụ cần output xác định**: chỉ định rõ method khi học cơ chế.

## 10. Những lỗi thường gặp và cách sửa

| Lỗi | Hậu quả | Cách làm đúng |
| --- | --- | --- |
| Tạo context trong tác vụ ngắn mà không bao giờ đóng | Callback hủy/resource cleanup có thể không được thực hiện có chủ đích | Dùng `try-with-resources` hoặc `finally { context.close(); }` |
| Đóng context ngay sau khi trả về một bean cho caller | Bean vẫn được dùng khi container đã shutdown | Để người tạo/chủ sở hữu context quyết định vòng đời |
| Service gọi `context.close()` sau một request | Tắt container chung và ảnh hưởng request khác | Không đặt trách nhiệm đóng context trong service nghiệp vụ |
| Gọi `context.close()` vừa trong thân `try` vừa khi TWR kết thúc | Logic hủy bị lặp ở cấp ý định, làm code khó kiểm soát | Để TWR đảm nhận `close()` một lần theo cấu trúc |
| Cho rằng `@Bean` nào cũng in gì đó khi đóng | Bean không có callback không phát sinh output | Gắn `destroyMethod` hoặc callback thích hợp nếu có công việc cần làm |
| Đánh đồng `close()` với garbage collection | Không hiểu tài nguyên ngoài heap/lifecycle | Tách đối tượng được thu gom với thao tác shutdown bean |
| Expect prototype luôn được tự động destroy | Tài nguyên của prototype bị bỏ quên | Giao trách nhiệm đóng cho owner hoặc dùng scope/cơ chế quản lý phù hợp |
| Tạo context bằng constructor refresh sẵn rồi cho rằng mọi lỗi tạo đều được TWR cleanup | Constructor có thể fail trước khi trở thành resource hợp lệ | Khi cần quản lý lỗi bootstrap, tạo context chưa refresh rồi `register/refresh` trong `try` |
| Dùng lại context sau close | Bean lookup hoặc lifecycle API không còn ở trạng thái dùng được | Tạo context mới ở một phiên làm việc mới khi cần |
| Dùng `System.exit(0)` như phương án cleanup | Có thể bỏ qua đường unwind Java bình thường | Kết thúc luồng qua return/exception, cho TWR đóng theo cấu trúc |
| Cấu hình sai `destroyMethod` | Có thể lỗi khi tạo bean hoặc callback không như mong đợi | Dùng tên method thật, không nhầm method có tham số hoặc tên typo |
| `javac` báo thiếu package Spring | Lỗi classpath, không phải lỗi `close()` | Dùng Maven hoặc tạo đúng classpath transitively |

**Không coi close là công cụ xử lý mọi lỗi:** nếu bean không đóng socket/file của nó theo hợp đồng, container không thể tự sửa logic đó. Cần unit test cho đối tượng sở hữu tài nguyên và smoke test cho context shutdown.

## 11. So sánh ba cách kết thúc lifecycle

| Phương án | Ưu điểm | Giới hạn |
| --- | --- | --- |
| `try (context) { ... }` | Rõ ràng chủ sở hữu, close theo lexical scope, tốt cho Java 17 | Chỉ dùng khi context thật sự do khối code đó sở hữu |
| `try { ... } finally { context.close(); }` | Dùng được ở code cũ và cho pattern phức tạp | Dễ lặp boilerplate hoặc quên `finally` nếu viết ẩu |
| `context.registerShutdownHook()` | Đăng ký JVM shutdown hook giúp đóng context khi JVM shutdown bình thường | Không thay thế close có chủ đích của tác vụ ngắn; không bảo đảm khi terminate/crash/kill -9 |

Đừng thường xuyên trộn `registerShutdownHook()` với TWR cho cùng context nếu không có lý do rõ; dễ làm mờ ownership và kỳ vọng thời điểm callback. Spring có cơ chế bảo vệ trạng thái khi shutdown, nhưng **thiết kế rõ một chủ sở hữu** luôn dễ kiểm tra hơn.

Nếu viết một ứng dụng dài hạn có lifecycle do framework điều phối, phải theo cơ chế shutdown của framework và hạ tầng triển khai, không kết thúc context của cả app sau khi xử lý một request.

## 12. Thực hành mở rộng và QA đầu ra

### Bài 1: Thêm một callback khác

Đăng ký bean phụ `TaskRecorder` có destroy callback riêng và cho `GreetingService` inject nó. Chạy và ghi nhận thứ tự callback thực tế. **Không dựa vào thứ tự khai báo method** để tạo hợp đồng thứ tự. Nếu bean B phụ thuộc A và đều có lifecycle, quan hệ phụ thuộc có thể ảnh hưởng trình tự hủy, nhưng cần thiết kế dựa trên dependency thật, không dựa vào log tình cờ.

### Bài 2: Ném lỗi trong thân try

Trong chương trình demo, thay lời gọi `service.greet(" Lan ")` bằng câu lệnh ném `IllegalStateException` **sau khi context.refresh() thành công**. Quan sát callback `destroy=ManagedAudit` vẫn chạy khi Java unwind vì exception. Không so đầu ra với sáu dòng happy path trong bài thử âm này.

### Bài 3: Làm refresh thất bại

Tạo một `@Bean` yêu cầu dependency bắt buộc mà cấu hình không đăng ký. Dùng pattern tạo context chưa refresh rồi gọi `refresh()` **bên trong try**. Quan sát context fail-fast; không giả định callback hủy của bean chưa được tạo cũng sẽ xuất hiện.

### Bài 4: Chạy với tên input khác

Trong happy path, đổi `" Lan "` thành `" Minh "`. Dự đoán hai dòng thay đổi:

~~~text
greeting=Xin chao, Minh!
audit=[Minh]
~~~

Các dòng trạng thái context/singleton và `destroy=ManagedAudit` không đổi trong cùng cấu hình.

### Bài 5: Viết test kiểm tra shutdown

Với Spring context do test tự tạo, có thể dùng bean fake giữ boolean `closed` và gắn destroy callback để chứng minh `closed==true` sau khi ra khỏi try. Hãy tránh gọi tiếp method nghiệp vụ trên resource đã đóng chỉ để test.

### Checklist để coi bài đạt

- [ ] Biết ai **sở hữu context** và vì sao cần đóng.
- [ ] Phân biệt `close()` của context với việc thu gom bộ nhớ.
- [ ] Dùng `try-with-resources` với context hỗ trợ `AutoCloseable`.
- [ ] Nắm khác biệt giữa `try (Type resource = ...)` và `try (resource)` của Java 9+.
- [ ] Với lỗi bootstrap, biết cách đưa `register/refresh` vào thân try đã sở hữu resource.
- [ ] Gắn callback cụ thể bằng `@Bean(destroyMethod="shutdown")`.
- [ ] Chạy JDK 17+ với Maven hoặc javac/java có classpath Spring.
- [ ] Đối chiếu đủ **sáu dòng stdout** của ví dụ.
- [ ] Phân biệt singleton destruction với prototype/non-managed object.
- [ ] Không đóng context được cấp bởi framework/đối tượng khác khi không sở hữu.
- [ ] Không kỳ vọng TWR luôn cleanup được khi JVM bị kill/crash.
- [ ] Biết dùng log và kiểm thử để xác minh callback thay vì suy đoán.

**Kết luận:** Try-with-resources không phải annotation của Spring; đó là **cơ chế ngôn ngữ Java** bảo đảm gọi `close()` cho resource đã được đăng ký khi rời khối. Vì Spring context triển khai hợp đồng đóng được, cơ chế Java ấy kích hoạt **quy trình shutdown của Spring**, trong đó có destroy callbacks trên các bean được quản lý phù hợp. Hãy **đóng context tại nơi sở hữu nó**, không phải tại mọi nơi đang sử dụng bean.

### Tài liệu chính thức

- [Spring — Shutting Down the Container Gracefully](https://docs.spring.io/spring-framework/reference/core/beans/factory-nature.html)
- [Spring — Using @Bean](https://docs.spring.io/spring-framework/reference/core/beans/java/bean-annotation.html)
- [Spring — Bean Scopes](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html)
- [Java Language Guide — try-with-resources](https://docs.oracle.com/javase/tutorial/essential/exceptions/tryResourceClose.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="first-application-008">So sánh ứng dụng có container với cách lắp ghép thủ công</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh ứng dụng có container với cách lắp ghép thủ công

## 1. Cùng một bài toán, hai cách tạo đồ thị đối tượng

Trong Java, một object thường cần object khác để hoàn thành nhiệm vụ. Ví dụ `GreetingService` cần một `Formatter` để tạo lời chào và một `ResourceProbe` mô phỏng tài nguyên phải đóng khi kết thúc ứng dụng.

Có **hai cách hợp lệ** để dựng đồ thị phụ thuộc ấy:

1. **Lắp ghép thủ công (manual wiring):** mã khởi động tự dùng `new`, truyền các object vào constructor, giữ reference và tự đóng tài nguyên đúng thời điểm.
2. **Dùng Spring IoC Container:** một class cấu hình khai báo bean definitions. `ApplicationContext` tạo các bean, giải quyết tham số phụ thuộc, cung cấp bean theo scope và gọi callback hủy thích hợp khi context đóng.

Điều quan trọng là **business logic có thể giữ nguyên**. Sự khác biệt chính nằm ở **ai chịu trách nhiệm tạo, chọn, kết nối, cấu hình và quản lý vòng đời của các đối tượng**, không phải ở thuật toán xử lý lời chào.

| Câu hỏi | Tự lắp ghép | Spring Core Container |
| --- | --- | --- |
| Ai tạo `Formatter`? | Composition root gọi `new VietnameseFormatter()` | Factory method `@Bean formatter()` trả về object |
| Ai chọn dependency cho service? | Caller truyền qua constructor | Container resolve tham số của `@Bean greetingService(...)` |
| Ai gọi `GreetingService` constructor? | Caller trực tiếp | Factory method của bean gọi constructor với dependency đã được Spring cấp |
| Ai quyết định chia sẻ instance? | Code tự giữ/reuse references | Bean scope; mặc định `singleton` theo definition/container |
| Ai gọi cleanup? | Caller phải chủ động | Container gọi destroy callback của các bean phù hợp khi `close()` |
| Có cần `ApplicationContext`? | Không | Có một implementation context/bean factory phù hợp |
| Logic nghiệp vụ có nhất thiết phụ thuộc Spring API? | Không | **Cũng không**; có thể giữ service là Java thuần |
| Có tự động đúng mọi cấu hình không? | Không | Không; thiếu/mơ hồ dependency vẫn làm bootstrap thất bại |

**Không đánh đồng “manual wiring” với thiết kế xấu.** Constructor injection là kỹ thuật Java thuần và cũng là nền để Spring inject các phụ thuộc. Với chương trình nhỏ, lắp ghép thủ công có thể là giải pháp dễ hiểu nhất. Khi dependency graph, môi trường cấu hình và lifecycle phức tạp, container giúp tập trung các trách nhiệm ấy.

## 2. Đọc đồ thị đối tượng và xác định ranh giới trách nhiệm

Hai đường khởi tạo sẽ tạo **cùng hình dạng đồ thị**:

~~~text
                    GreetingService
                     /           \
                    v             v
               Formatter      ResourceProbe
                    |              |
                    v              v
            VietnameseFormatter  close()

    MANUAL COMPOSITION                SPRING COMPOSITION
    ------------------                ------------------
    new VietnameseFormatter()          @Bean Formatter
    new ResourceProbe()                 @Bean ResourceProbe
    new GreetingService(f, p)           @Bean GreetingService(f, p)
    gọi p.close()                       context.close()
~~~

`GreetingService` chỉ nhận **hai reference**, không cần biết chúng đến từ biến local của `main` hay từ container. Trong ví dụ, nó **không gọi `new VietnameseFormatter()` bên trong constructor**; nếu làm vậy, service bị buộc vào implementation và khó thay bằng fake trong kiểm thử.

**Composition root** là nơi ứng dụng tập trung việc kết nối các phụ thuộc (thường tại `main`, bootstrap hoặc một module cấu hình). Ở phương án thủ công, `main` tự thực hiện; ở phương án Spring, `AppConfig` diễn đạt graph bằng `@Bean`, còn context tổ chức việc instantiate và wiring.

**Ranh giới ownership:** nơi **tạo/đăng ký** tài nguyên thường cần quyết định nơi **đóng** nó. Spring hỗ trợ callback theo scope, nhưng không thể tự dọn mọi object do code Java tùy ý tạo ra ở bên ngoài container.

## 3. Cấu trúc project Java 17 + Spring Framework thuần

Chúng ta đặt **cả hai cách trong cùng một file Java**, để khác biệt không đến từ hai implementation business logic. Không cần Spring Boot, database, component scanning hay HTTP server.

Yêu cầu: **JDK 17+**, **Maven 3.9+**, và quyền truy cập Maven Central/mirror ở lần tải dependencies đầu. Sử dụng Spring Framework `7.0.9` để phiên bản trong ví dụ nhất quán.

~~~text
manual-vs-spring-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── ManualVsSpringDemo.java
~~~

Tạo tệp `pom.xml`:

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>manual-vs-spring-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>7.0.9</version>
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
                    <mainClass>demo.ManualVsSpringDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

`spring-context` cung cấp `AnnotationConfigApplicationContext`, `@Configuration` và `@Bean`. Maven giải quyết các Spring dependencies cần thiết transitively, nên bạn không phải tải thủ công từng JAR. `maven.compiler.release=17` yêu cầu biên dịch theo API và bytecode Java 17.

**Lưu ý:** phần manual trong file vẫn có thể chạy **mà không có container** về mặt thiết kế; nhưng vì **cùng tệp** còn chứa import/class cấu hình Spring, việc biên dịch toàn bộ tệp demo vẫn cần `spring-context` trên classpath. Muốn làm chương trình hoàn toàn không có thư viện Spring, hãy tách các interface/class nghiệp vụ và entry point manual ra một project Java thuần.

## 4. Mã hoàn chỉnh: một service, hai composition roots

Tạo `src/main/java/demo/ManualVsSpringDemo.java`:

~~~java
package demo;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public final class ManualVsSpringDemo {

    private ManualVsSpringDemo() {
    }

    // Hợp đồng business: không nhập bất kỳ API Spring nào.
    interface Formatter {
        String format(String name);
    }

    static final class VietnameseFormatter implements Formatter {
        @Override
        public String format(String name) {
            return "Xin chao, " + name;
        }
    }

    // Mô phỏng một resource có trạng thái đóng.
    // Không phải kết nối mạng/file thật.
    public static final class ResourceProbe {
        private boolean closed;

        public boolean isClosed() {
            return closed;
        }

        public void close() {
            closed = true;
        }
    }

    static final class GreetingService {
        private final Formatter formatter;
        private final ResourceProbe probe;

        GreetingService(Formatter formatter, ResourceProbe probe) {
            this.formatter = formatter;
            this.probe = probe;
        }

        String greet(String name) {
            return formatter.format(name) + "!";
        }

        boolean hasDependencies(Formatter f, ResourceProbe p) {
            return formatter == f && probe == p;
        }
    }

    // Spring-only composition root.
    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {
        @Bean
        public Formatter formatter() {
            return new VietnameseFormatter();
        }

        @Bean(destroyMethod = "close")
        public ResourceProbe resourceProbe() {
            return new ResourceProbe();
        }

        @Bean
        public GreetingService greetingService(
                Formatter formatter, ResourceProbe resourceProbe) {
            return new GreetingService(formatter, resourceProbe);
        }
    }

    public static void main(String[] args) {
        // ----- 1) Manual wiring -----
        Formatter manualFormatter = new VietnameseFormatter();
        ResourceProbe manualProbe = new ResourceProbe();
        GreetingService manualService =
                new GreetingService(manualFormatter, manualProbe);

        String manualMessage = manualService.greet("Lan");
        System.out.println("manual.message=" + manualMessage);
        System.out.println("manual.wiring="
                + manualService.hasDependencies(manualFormatter, manualProbe));
        System.out.println("manual.closed.before=" + manualProbe.isClosed());

        // Caller sở hữu và tự đóng resource trong ví dụ manual.
        manualProbe.close();
        System.out.println("manual.closed.after=" + manualProbe.isClosed());

        // ----- 2) Spring Container wiring -----
        ResourceProbe managedProbe;
        String springMessage;

        try (var context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {
            Formatter managedFormatter = context.getBean(Formatter.class);
            managedProbe = context.getBean(ResourceProbe.class);
            GreetingService managedService =
                    context.getBean(GreetingService.class);

            springMessage = managedService.greet("Lan");
            System.out.println("spring.message=" + springMessage);
            System.out.println("spring.wiring="
                    + managedService.hasDependencies(
                            managedFormatter, managedProbe));
            System.out.println("spring.singleton="
                    + (managedService == context.getBean(GreetingService.class)));
            System.out.println("spring.closed.before=" + managedProbe.isClosed());
        }

        // try-with-resources đã gọi context.close(),
        // và Spring gọi destroyMethod="close" trên bean ResourceProbe.
        System.out.println("spring.closed.after=" + managedProbe.isClosed());
        System.out.println("same.business.result="
                + manualMessage.equals(springMessage));
    }
}
~~~

### Giải thích vì sao so sánh này công bằng

1. Hai cách dùng **cùng** `GreetingService`, `Formatter`, `VietnameseFormatter` và `ResourceProbe`. Không viết thuật toán riêng cho Spring.
2. Manual `main` tạo formatter và probe rồi đưa **chính hai reference ấy** vào constructor `GreetingService`.
3. Spring `AppConfig` khai báo ba `@Bean` factory methods. Khi tạo bean `greetingService`, Spring resolve `Formatter` và `ResourceProbe` từ context theo type rồi truyền cho factory method.
4. `@Configuration(proxyBeanMethods = false)` phù hợp vì các phương thức `@Bean` **không gọi trực tiếp lẫn nhau**. Nếu trong method `greetingService()` tự gọi `formatter()` ở chế độ này, đó có thể là lời gọi Java thông thường tạo thêm object ngoài lookup singleton. Ví dụ tránh sai sót ấy bằng injection qua **tham số factory method**.
5. `GreetingService` và các dependency của nó dùng scope mặc định `singleton` trong context hiện tại. Vì vậy hai lần lookup cùng bean `GreetingService` trả cùng reference.
6. Với manual, programmer chủ động gọi `manualProbe.close()`. Với Spring, factory `@Bean(destroyMethod="close")` chỉ rõ callback; `try-with-resources` đóng context và Spring sẽ gọi callback ấy theo vòng đời singleton.

`ResourceProbe` chỉ là **dụng cụ quan sát trạng thái** để tạo kết quả xác định. Trong ứng dụng thật, đối tượng cần đóng có thể là connection pool, client hoặc file handle; cách đóng phải theo API và ownership cụ thể. Tránh dùng một biến boolean `closed` như bằng chứng bảo đảm đã giải phóng tài nguyên hệ điều hành.

## 5. Chạy chương trình và đối chiếu chính xác stdout

Trong terminal tại thư mục `manual-vs-spring-demo` (nơi có `pom.xml`), kiểm tra phiên bản công cụ:

~~~bash
java -version
javac -version
mvn -version
~~~

Sau đó biên dịch và chạy:

~~~bash
mvn -q clean compile exec:java
~~~

Lệnh Maven gọi compiler với target Java 17, tạo class và chạy `demo.ManualVsSpringDemo` với classpath gồm Spring libraries.

**Mười dòng stdout chính xác do `System.out.println` trong `main` in ra**, không tính output của lệnh kiểm tra môi trường, Maven build hoặc logger:

~~~text
manual.message=Xin chao, Lan!
manual.wiring=true
manual.closed.before=false
manual.closed.after=true
spring.message=Xin chao, Lan!
spring.wiring=true
spring.singleton=true
spring.closed.before=false
spring.closed.after=true
same.business.result=true
~~~

### Ý nghĩa từng dòng

| Quan sát | Giải thích |
| --- | --- |
| `manual.message` và `spring.message` giống nhau | Business behavior **không đổi** dù cách tạo graph khác |
| `manual.wiring=true` | Service giữ đúng hai object mà manual composition root truyền vào |
| `spring.wiring=true` | Bean service giữ đúng hai bean dependency lấy lại từ cùng context |
| `spring.singleton=true` | Hai lần `getBean(GreetingService.class)` trả **cùng instance** trong context |
| `manual.closed.before=false` / `after=true` | Manual caller trực tiếp gọi `close()` của probe |
| `spring.closed.before=false` / `after=true` | `ApplicationContext.close()` gọi callback destroy đã đăng ký |
| `same.business.result=true` | Hai cách lắp ráp tạo cùng kết quả `String` từ cùng thuật toán |

`==` trong `hasDependencies` và `spring.singleton` được dùng đúng mục đích: **so danh tính reference**. `String.equals` được dùng đúng mục đích: **so giá trị lời chào**. Không sử dụng `==` để kết luận hai chuỗi khác object có cùng nội dung hay không.

**Trường hợp thay đổi code:** nếu thêm một formatter bean thứ hai trong context mà không có quy tắc phân giải, `getBean(Formatter.class)` hoặc injection có thể mơ hồ. Khi ấy output chuẩn **không còn áp dụng**, và context có thể fail lúc startup trước khi in phần Spring. Không chép nguyên thông báo exception vì nó có thể khác theo patch.

## 6. Container có thay thế constructor injection không?

**Không.** Constructor injection vốn là một kỹ thuật thiết kế Java thuần. Đoạn:

~~~java
Formatter f = new VietnameseFormatter();
ResourceProbe p = new ResourceProbe();
GreetingService service = new GreetingService(f, p);
~~~

không dùng Spring vẫn là **Dependency Injection thủ công**, vì dependency được **truyền vào** service thay vì do service tự tìm/tự khởi tạo.

Spring DI có điểm khác: **container tự giải quyết** dependency trên cơ sở các bean definitions và cấu hình scope/qualifier, gọi factory/constructor thích hợp và quản lý lifecycle theo hợp đồng của container. Trong demo, phương thức:

~~~java
@Bean
public GreetingService greetingService(
        Formatter formatter, ResourceProbe resourceProbe) {
    return new GreetingService(formatter, resourceProbe);
}
~~~

không tạo `Formatter` hoặc `ResourceProbe` bên trong nó. Spring cung cấp cả hai tham số khi khởi tạo bean, rồi code Java dùng chúng theo cách thường. Các đoạn trên là **snippet minh họa**, phải đặt trong class/ngữ cảnh có import/type phù hợp; chương trình biên dịch độc lập là toàn bộ file ở mục 4.

Tránh hai hiểu lầm:
- **“Có Spring thì không được dùng `new`.”** Sai. Spring vẫn dùng constructor/factory method; trong `@Bean`, bạn hoàn toàn có thể `return new ...`. Quan trọng là container đăng ký/quản lý **object trả về**.
- **“Có `@Autowired` thì code business tự động độc lập.”** Không nhất thiết. Để business dễ test, hãy giữ các phụ thuộc rõ ràng qua constructor/interface, tránh truy cập global context không cần thiết.

## 7. Khi đồ thị lớn lên: nơi lợi ích và chi phí xuất hiện

Giả sử `GreetingService` được thay bằng hệ có `OrderService`, `PaymentClient`, `TaxPolicy`, `InventoryRepository` và `AuditPublisher`.

Với manual wiring, bạn có thể viết một composition root **rất rõ ràng**:

~~~java
// Pseudocode Java: các class ở ví dụ kiến trúc này chưa được định nghĩa.
var repository = new InventoryRepository(config);
var taxPolicy = new TaxPolicy(config);
var payment = new PaymentClient(config);
var audit = new AuditPublisher(config);
var orderService = new OrderService(
        repository, taxPolicy, payment, audit);
~~~

Điểm mạnh: nhìn vào code biết chính xác implementation nào được tạo, thứ tự/ownership dễ kiểm soát nếu thiết kế tốt; không cần framework. Điểm yếu: khi số cấu hình, scope, alternative implementations, environment và lifecycle tăng, phần lắp ghép có thể dài và dễ lệch giữa nhiều entry points.

Với Spring, composition root được biểu diễn bằng các bean definitions:

~~~java
// Pseudocode cấu hình: cần định nghĩa các type và cấu hình thật.
@Configuration(proxyBeanMethods = false)
class OrderConfig {
    @Bean
    OrderService orderService(
            InventoryRepository repository,
            TaxPolicy taxPolicy,
            PaymentClient payment,
            AuditPublisher audit) {
        return new OrderService(repository, taxPolicy, payment, audit);
    }
}
~~~

Điểm mạnh: container quản lý graph, resolve dependency theo quy tắc, hỗ trợ scopes, lifecycle, condition/configuration và nhiều extension points. Điểm yếu: **trừu tượng nhiều hơn**, startup/metadata/proxy có chi phí, và lỗi thiếu/mơ hồ bean đôi khi chỉ xuất hiện lúc tạo context. Một cấu hình Spring sai không được container “đoán” rồi sửa thành đúng.

**Điều Spring không tự làm:** viết business logic, bảo đảm thread safety cho singleton, xử lý mọi vòng phụ thuộc constructor, tự động giải phóng object prototype sau khi caller nhận, hoặc thay thế unit test. Container là công cụ quản lý đối tượng chứ không phải “bộ sửa kiến trúc” tự động.

## 8. So sánh vòng đời và phạm vi chia sẻ

### Manual wiring

- Bạn tự quyết định reference có được chia sẻ không: truyền cùng `Formatter` vào 5 services hay tạo 5 formatter khác nhau đều là quyết định của code.
- Một resource do code tạo phải có owner chịu trách nhiệm cleanup. Với resource implement `AutoCloseable`, có thể dùng `try-with-resources` như Java thông thường.
- Nếu composition root quên đóng object hoặc đóng hai lần không an toàn, Spring không thể tự chữa hành vi xảy ra hoàn toàn bên ngoài container.

### Container wiring

- `singleton` là scope mặc định: thường một instance được chia sẻ **trên mỗi bean definition trong mỗi context**.
- `prototype` cấp instance mới khi yêu cầu. Nếu inject prototype trực tiếp vào constructor của một singleton, dependency đó thường chỉ được resolve **một lần khi singleton được tạo**, không tự đổi object mỗi lần gọi method.
- Bean singleton không `@Lazy` trong `ApplicationContext` thường được **khởi tạo ngay lúc refresh**, không nhất thiết đến khi gọi `getBean` lần đầu.
- Khi `close()`, Spring gọi destruction callbacks của bean phù hợp; với prototype, container thường **không tự quản lý toàn bộ destruction callbacks** sau khi giao instance cho caller.
- Nếu cần request/session scope, phải có ngữ cảnh web phù hợp; console context của bài không tự hỗ trợ HTTP request scope.

**Phạm vi trách nhiệm của `ResourceProbe`:** `@Bean(destroyMethod="close")` bảo đảm callback được đăng ký cho bean và được Spring xử lý khi context đóng bình thường. Nó **không** biến một object Java khác bạn tạo ngoài container thành Spring bean và **không** bảo đảm callback chạy nếu JVM bị kill cưỡng bức.

## 9. Kiểm thử: nên dùng manual fake hay context thật?

Một trong những lý do tốt nhất để thiết kế service nhận dependency qua constructor là **unit test không cần khởi động Spring**.

Ví dụ đặt trong ngữ cảnh cùng các type ở mục 4:

~~~java
Formatter fake = name -> "TEST-" + name;
ResourceProbe probe = new ResourceProbe();
GreetingService service = new GreetingService(fake, probe);

System.out.println(service.greet("Lan"));
probe.close();
~~~

Stdout do đoạn minh họa ấy in ra:

~~~text
TEST-Lan!
~~~

`Formatter` là interface có một abstract method, nên Java 17 cho phép dùng lambda. Test kiểu này kiểm tra **logic của service** mà không phụ thuộc bean registration, context startup hoặc auto-configuration. Đây là snippet cần đặt trong class/method có quyền truy cập các nested types; nó **không** phải một file `.java` độc lập.

Khi nào cần tạo Spring context trong test? Khi bạn muốn xác minh **chính wiring/configuration/bean scopes/lifecycle** hoặc extension points của framework. Một test context có thể phát hiện thiếu bean hoặc lựa chọn sai `@Qualifier` mà unit test thuần Java không kiểm tra.

**Phân chia mục tiêu kiểm thử:**
- Unit test Java thuần: logic thuật toán, edge cases, fake dependency, tốc độ nhanh.
- Integration test có context: graph cấu hình, DI, proxy/lifecycle và behavior gắn với framework.
- End-to-end test: các tầng bên ngoài như HTTP/database/messaging khi có nhu cầu.

Không khởi động context cho mọi unit test chỉ vì dự án sử dụng Spring. Tương tự, không suy luận rằng unit test pass thì Spring config luôn đúng: hai loại kiểm tra chứng minh các điều khác nhau.

## 10. Những lỗi thường gặp khi so sánh

| Nhận định/sai sót | Cách hiểu hoặc cách xử lý đúng |
| --- | --- |
| “Manual wiring không phải DI.” | Truyền dependency từ caller qua constructor **là DI thủ công** |
| “Spring tạo object mà không có `new`.” | Constructor/factory method vẫn tạo object Java; Spring tổ chức và quản lý |
| “Hai cách phải khác thuật toán.” | **Có thể dùng cùng class service**; chỉ đổi cách tạo/cấp dependency |
| “`@Bean` trên method tự đủ mà không có context.” | Cần class cấu hình được đăng ký/được Spring xử lý trong context |
| “Bean `@Component` ở đâu cũng tự được quét.” | Phải cấu hình scan/registration đúng package |
| “Singleton nghĩa là một object duy nhất trên JVM.” | Mặc định Spring singleton theo definition và container |
| “Spring sẽ luôn chọn đúng một interface implementation.” | Nhiều bean cùng type không có qualifier/primary phù hợp có thể gây ambiguity |
| “Gọi `@Bean` method trực tiếp luôn lấy singleton.” | Với `proxyBeanMethods=false`, lời gọi thường có thể tạo object mới |
| “Dùng Spring thì không cần quản lý tài nguyên.” | Cần scope, destroy callback, shutdown đúng; object ngoài context vẫn thuộc owner riêng |
| “Dùng `==` để kiểm tra business string bằng nhau.” | Dùng `.equals` cho giá trị; `==` ở đây chỉ để quan sát reference |
| “Prototype sẽ đổi dependency trong singleton ở mỗi lần gọi.” | Constructor injection thường xảy ra một lần; cần provider/lookup có chủ đích |
| “Boot và Core Container là một.” | Spring Boot dùng Core Container của Framework, thêm conventions/auto-config |

### Nếu chạy không giống stdout

- Kiểm tra đã **thay đổi source** khi làm bài tập hay chưa; bản gốc dùng ba singleton beans trong một context.
- Nếu Spring startup ném `NoSuchBeanDefinitionException`/`NoUniqueBeanDefinitionException`, kiểm tra `@Bean` methods và parameter types; đó là vấn đề graph/metadata, không mặc định là lỗi Maven.
- Nếu `package org.springframework... does not exist`, kiểm tra dependency `spring-context`, Maven resolution và compile classpath.
- Nếu `spring.closed.after=false`, xác minh bean `ResourceProbe` là Spring-managed singleton, `destroyMethod="close"` và context đã đóng bình thường. Đừng nhầm đóng context với garbage collection.
- Nếu Maven/logging in thêm dòng, đối chiếu **10 dòng của ứng dụng**, không đếm toàn bộ output của terminal.
- Nếu `manual.wiring=false`, kiểm tra có tạo thêm dependency bằng `new` giữa lúc inject và lúc so reference.
- Nếu `same.business.result=false`, xem implementation/đầu vào có còn giống nhau không; khác cách wiring **không bắt buộc** phải tạo ra cùng kết quả nếu bạn đã cấu hình dependency khác nhau.

## 11. Bài tập có kiểm chứng

Thực hiện từng bài riêng rồi khôi phục mã gốc để đối chiếu stdout:

1. **Đổi implementation:** viết `EnglishFormatter`, trả `"Hello, " + name`, rồi thay implementation ở *cả hai* composition roots. Kiểm tra `same.business.result` vẫn `true`.
2. **Đổi một bên:** chỉ thay formatter bean trong `AppConfig` mà giữ manual formatter cũ. Dự đoán `same.business.result=false` dù `spring.wiring=true`.
3. **Thêm dependency:** tạo `Clock` (fake trả giờ cố định) và inject vào service. So sánh số chỗ phải thay trong manual root và `AppConfig`. Tránh đọc đồng hồ hệ thống trực tiếp nếu muốn expected output bất biến.
4. **Đổi scope:** gắn `@Scope("prototype")` lên bean `greetingService`, gọi `getBean` hai lần và dự đoán `spring.singleton=false`. Nhãn nên đổi thành `spring.sameInstance` để đúng nghĩa.
5. **Thiếu bean:** tạm xóa `@Bean formatter()` trong bản sao. Context có thể fail startup do `GreetingService` không resolve được tham số `Formatter`.
6. **Nhiều candidate:** thêm một bean `Formatter` khác, thử `@Primary` hoặc `@Qualifier` để kiểm soát lựa chọn.
7. **Quên cleanup:** bỏ `manualProbe.close()` và quan sát `manual.closed.after`. Sau đó khôi phục code và mô tả ảnh hưởng nếu `ResourceProbe` là file/network resource thật.
8. **Đóng context:** bỏ `try-with-resources` trong bản sao rồi chủ động `context.close()` tại điểm thích hợp; giải thích cách xử lý exception để tránh rò tài nguyên.
9. **Test không Spring:** dùng lambda fake ở mục 9 để kiểm tra service, rồi giải thích test nào cần context để xác minh `@Bean`/lifecycle.
10. **Hai contexts:** tạo hai context với cùng `AppConfig`, so reference của hai `GreetingService`. Giải thích singleton không đồng nghĩa object được chia sẻ giữa hai contexts.

### Checklist đã hiểu

- [ ] Nêu được phần giống và khác giữa **manual constructor injection** với **container-managed DI**.
- [ ] Chỉ ra **composition root** trong hai cách và nơi quyết định implementation.
- [ ] Xác định được Spring quản lý bean **trong context**, không phải mọi object từ `new`.
- [ ] Giải thích vì sao `manual.wiring`/`spring.wiring` đều `true`.
- [ ] Phân biệt `spring.singleton` với `same.business.result`.
- [ ] Dự đoán chính xác 10 dòng stdout khi chạy file Java không chỉnh sửa.
- [ ] Biết đóng resource theo ownership, chọn unit test không context và test wiring có context.

## 12. Tóm tắt và tài liệu tra cứu

**Kết luận chính:** Spring Core Container không thay thế các nguyên tắc thiết kế Java. Nó **thay thế phần lắp ghép và quản lý đồ thị đối tượng do ứng dụng tự viết** bằng một cơ chế được cấu hình tập trung, có các quy tắc bean scope, dependency resolution và lifecycle. Cả hai cách đều có thể dùng constructor injection và giữ business class không phụ thuộc framework.

- Với đồ thị nhỏ, lắp ghép thủ công rõ ràng và có thể đủ dùng.
- Với đồ thị lớn, nhiều cấu hình và lifecycle, Spring có thể giảm mã bootstrap lặp lại nhưng đòi hiểu bean registration, context, scope và error diagnostics.
- Dù dùng cách nào, hãy giữ dependency tường minh, xác định ownership, viết test đúng mức và không dựa vào “container magic” thay cho việc hiểu Java.

Tài liệu chính thức:

- [Spring – Introduction to the IoC Container](https://docs.spring.io/spring-framework/reference/core/beans/introduction.html)
- [Spring – Dependencies and Configuration in Detail](https://docs.spring.io/spring-framework/reference/core/beans/dependencies.html)
- [Spring – Dependency Injection](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html)
- [Spring – Java-based Container Configuration](https://docs.spring.io/spring-framework/reference/core/beans/java.html)
- [Spring – Using the @Bean Annotation](https://docs.spring.io/spring-framework/reference/core/beans/java/bean-annotation.html)
- [Spring – Bean Scopes](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html)
- [Spring – Bean Lifecycle](https://docs.spring.io/spring-framework/reference/core/beans/factory-nature.html)
- [Spring Framework 7.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes)

Bài tập cốt lõi: giữ nguyên `GreetingService`, thay riêng **composition root** giữa `new` và `ApplicationContext`; đo **kết quả**, **reference dependencies**, **scope** và **cleanup**. Khi bốn yếu tố ấy rõ ràng, bạn đã hiểu sự khác biệt thực tế giữa ứng dụng Java tự lắp ghép với ứng dụng có Spring container.

</details>

- [Quay lại đầu trang](#back-to-top)
