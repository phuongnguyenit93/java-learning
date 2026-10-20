<a id="back-to-top"></a>

# Tích hợp và triển khai

## Menu
- [Khai báo công cụ biên dịch hoặc bộ dệt trong quy trình xây dựng](#aspect-integration-deployment-001)
- [Tách mã aspect, cấu hình và thư viện dùng chung](#aspect-integration-deployment-002)
- [Đóng gói và kiểm tra tạo tác đã dệt](#aspect-integration-deployment-003)
- [Quản lý phiên bản công cụ, định dạng mã và thư viện phụ thuộc](#aspect-integration-deployment-004)
- [Triển khai dệt khi nạp trong môi trường có nhiều bộ nạp](#aspect-integration-deployment-005)
- [Loại trừ mã sinh, thư viện ngoài và công cụ quan sát khi cần](#aspect-integration-deployment-006)
- [Kiểm tra tương thích với kiểm thử, đo độ bao phủ và gỡ lỗi](#aspect-integration-deployment-007)
- [Tạo bản dựng lặp lại và phát hiện thay đổi kết quả weaving](#aspect-integration-deployment-008)
- [Bật theo giai đoạn, giám sát và chuẩn bị phương án quay lui](#aspect-integration-deployment-009)

## <a id="aspect-integration-deployment-001">Khai báo công cụ biên dịch hoặc bộ dệt trong quy trình xây dựng</a>

<details>
<summary>Xem chi tiết</summary>

# Khai báo công cụ biên dịch hoặc bộ dệt trong quy trình xây dựng AOP

## 1. Mục tiêu

Khi dùng **Aspect-Oriented Programming (AOP)**, khai báo một `aspect` hay `advice` trong mã nguồn chưa đủ để hành vi cắt ngang xuất hiện lúc chương trình chạy. Tùy kỹ thuật AOP, quy trình build phải **biên dịch aspect**, **dệt (weave) aspect vào những lớp mục tiêu**, hoặc **đóng gói bộ dệt để xử lý khi JVM nạp lớp**. Bài này giải thích cách chọn và khai báo các bước đó trong build, cách quản lý dependency, cách kiểm chứng file đầu ra và các lỗi cấu hình thường gặp.

Một cấu hình build đúng cần trả lời được:

1. **Ai thực thi biên dịch/dệt?** Ví dụ: trình biên dịch/weaver `ajc`, một tác vụ build tùy biến, plugin, hoặc Java agent.
2. **Đầu vào nằm ở đâu?** Mã Java, mã aspect, thư viện chứa aspect, các file `.class`/JAR đã biên dịch và các dependency liên quan.
3. **Dệt vào lúc nào?** Khi biên dịch, sau khi biên dịch, hay khi JVM nạp lớp.
4. **Đầu ra thực sự nào được chạy/phát hành?** Các lớp sau weaving, file JAR chứa chúng, hoặc ứng dụng nguyên bản kèm agent và cấu hình.
5. **Làm sao chứng minh advice đã được nối?** Thông báo `weave info`, smoke test, bytecode, và kiểm tra lệnh chạy.

Về bản chất, pointcut mô tả **những join point cần chọn**; compiler/weaver hoặc interceptor infrastructure quyết định **liệu hành vi đó được tích hợp vào luồng chạy hay không**. Một pointcut đúng nhưng khâu dệt không hoạt động vẫn có thể khiến advice hoàn toàn vắng mặt.

## 2. Phân loại kỹ thuật AOP và trách nhiệm của build

| Cơ chế | Khi nào tích hợp advice? | Điều cần khai báo trong build/triển khai |
| --- | --- | --- |
| **Compile-time weaving (CTW)** | Biên dịch nguồn và weave trong cùng bước | Compiler hiểu aspect, nguồn Java/aspect, compilation level, classpath, output |
| **Post-compile / binary weaving (PCW)** | Sau khi Java đã tạo `.class` hoặc JAR | Weaver task, binary input (`-inpath`), aspect nguồn hoặc thư viện (`-aspectpath`), output riêng |
| **Load-time weaving (LTW)** | Khi JVM classloader định nghĩa lớp | Aspect đã biên dịch, `aspectjweaver` agent, `META-INF/aop.xml`, runtime classpath, lệnh khởi động |
| **Proxy/interception AOP** | Lời gọi đi qua proxy/interceptor lúc chạy | Thư viện tạo proxy, cấu hình lắp ghép interceptor và phân phối đúng tham chiếu proxy; có thể không cần bytecode weaver |

**Không được nhầm proxy AOP với binary weaving.** Một build chỉ chạy `javac` vẫn có thể dùng AOP dựa trên proxy ở runtime. Ngược lại, AspectJ-style `execution`/`call`/`get`/`set` weaving thường cần compiler/weaver hỗ trợ các join point đó. Việc proxy bị bỏ qua bởi self-invocation không suy ra rằng một execution join point đã được weave cũng bị bỏ qua.

Phần hướng dẫn cụ thể sau dùng **AspectJ và công cụ `ajc`** làm ví dụ cho AOP dệt mã Java; các nguyên tắc về thứ tự tác vụ, tính đúng đắn của artifact và kiểm thử áp dụng rộng hơn. Cú pháp và hỗ trợ join point của các công cụ AOP khác có thể khác nhau.

## 3. Xác định rõ hợp đồng đầu vào/đầu ra

Một pipeline dệt mã có thể biểu diễn như sau:

```text
Java source + Aspect source + dependencies
                       |
                       v
              Aspect-aware compiler (CTW)
                       |
                       v
                 Woven .class/JAR
                       |
               test -> package -> run

Java source --javac--> Plain .class/JAR
                              |
Plain .class/JAR + Aspect binary + weaver
                              |
                              v
                       Woven .class/JAR (PCW)
                              |
                       test -> package -> run

Plain .class/JAR + compiled aspects + aop.xml
                              |
                 JVM + weaving javaagent (LTW)
                              |
                       Woven code in memory
```

Khác biệt cốt lõi:

- **CTW:** mã đã dệt được lưu thành artifact sau biên dịch; lúc chạy thường chỉ cần các runtime dependencies tương ứng. Không cần agent chỉ để kích hoạt những advice đã weave.
- **PCW:** giữ `plain classes` và `woven classes` ở **hai thư mục/JAR khác nhau**, nhờ đó tránh ghi đè đầu vào và biết chính xác artifact nào phải được phát hành.
- **LTW:** artifact ứng dụng có thể vẫn là class chưa weave; tính năng AOP phụ thuộc cả **lệnh khởi động JVM**, `aspectjweaver`, aspect và cấu hình được tìm thấy ở runtime. Chỉ thêm dependency lúc biên dịch không tự bật agent.
- **Proxy:** build chịu trách nhiệm có đúng thư viện/runtime configuration, nhưng pointcut có thể được đánh giá tại proxy boundary thay vì biến đổi bytecode.

Trong mọi pipeline, **test phải chạy trên output mà production sẽ sử dụng**. Nếu test chạy trên các class chưa weave còn gói phát hành chứa woven classes (hoặc ngược lại), kết quả test sẽ gây hiểu nhầm.

## 4. Dependency và các tham số thường gặp của `ajc`

Ba artifact có vai trò khác nhau trong một cấu hình AspectJ điển hình:

| Artifact | Vai trò |
| --- | --- |
| `org.aspectj:aspectjtools` | Chứa compiler/weaver `org.aspectj.tools.ajc.Main`, dùng khi build |
| `org.aspectj:aspectjrt` | Runtime các type cần thiết cho aspect đã biên dịch/weave, dùng ở runtime của ứng dụng |
| `org.aspectj:aspectjweaver` | Weaving engine và Java agent; quan trọng với LTW, không tự biến build CTW thành LTW |

Các tùy chọn chính:

| Tùy chọn `ajc` | Ý nghĩa | Nhầm lẫn thường gặp |
| --- | --- | --- |
| `-classpath` | Các type/dependency mà compiler cần để phân giải tên và liên kết | Nó **không đồng nghĩa** danh sách các class phải weave |
| `-inpath` | Thư mục/JAR chứa bytecode đầu vào để **binary weave** | Không lấy đầu vào trực tiếp từ mọi thư viện chỉ vì chúng nằm trên classpath |
| `-aspectpath` | Thư mục/JAR chứa **binary aspects** để dệt vào nguồn/inpath | Không thay thế `-classpath` cho mọi class được tham chiếu |
| `-d` | Thư mục đặt các class kết quả | Không ghi lên cùng nơi lưu inpath khi có thể tránh |
| `-outjar` | Ghi kết quả build thành JAR | Đảm bảo JAR được phát hành chính là kết quả đã weave |
| `-source`, `-target`, `-17` | Chọn phiên bản cú pháp/bytecode tương ứng | Không mặc định tương thích với JDK chạy `ajc` nếu compiler quá cũ |
| `-showWeaveInfo` | In ra thông báo về các lời ghép join point/advice | Thiếu output có thể do matcher không khớp hoặc không có class cần weave |
| `-Xlint:warning` / `-Xlint:error` | Phát hiện vấn đề AOP đáng chú ý | Không phải mọi warning đều chứng minh runtime sai; cần phân loại |
| `-sourceroots` / `@argfile` | Chỉ ra tập nguồn/argument file | `ajc` không tự tìm mọi nguồn cần thiết như mong đợi từ `javac` |

`-inpath` và `-aspectpath` nhận một đối số đường dẫn, dùng dấu phân cách `:` trên Linux/macOS hoặc `;` trên Windows. Khi gọi từ build tool, nên dựng classpath bằng API của tool thay vì tự ghép theo hệ điều hành. Các binary aspect từ `-aspectpath` phải tương thích với compiler đang weave; giữ phiên bản runtime/compiler phù hợp để dễ tái hiện.

Không có một “bản AspectJ mới nhất” cố định cho mọi dự án. Khi nâng Java language level, kiểm tra ma trận hỗ trợ JDK/compiler của phiên bản `ajc` đã chọn và khóa phiên bản trong build. Những phiên bản viết dưới đây là **ví dụ xác định để học và tái hiện**, cần rà soát theo chính sách dependency của dự án khi áp dụng lâu dài.

## 5. Ví dụ tối thiểu chạy được: Java 17 + compile-time weaving

### 5.1. Cấu trúc file

```text
aop-build-demo/
├── src/
│   └── demo/
│       ├── Main.java
│       └── TraceAspect.aj
└── lib/
    ├── aspectjtools-1.9.22.jar
    └── aspectjrt-1.9.22.jar
```

Tải hai JAR đúng tọa độ Maven `org.aspectj:aspectjtools:1.9.22` và `org.aspectj:aspectjrt:1.9.22` từ kho dependency tin cậy. Đây là hai dependency riêng; tên file trong ví dụ giả định JAR đã được đặt trong `lib/`.

**`src/demo/Main.java`**:

```java
package demo;

public final class Main {
    public static void main(String[] args) {
        work();
    }

    static void work() {
        System.out.println("business work");
    }
}
```

**`src/demo/TraceAspect.aj`**:

```java
package demo;

public aspect TraceAspect {
    before(): execution(void demo.Main.work()) {
        System.out.println("advice before work");
    }
}
```

Đây là **AspectJ code-style aspect**, file `.aj` cần compiler hiểu AspectJ. Lệnh `javac` thuần túy không biên dịch cú pháp `public aspect` và `before(): ...`. Pointcut chọn execution của `Main.work()`, không chọn `Main.main()`.

### 5.2. Biên dịch và chạy trên Linux/macOS

Chạy trong thư mục `aop-build-demo/` với JDK 17+ và hai JAR đã có:

```bash
mkdir -p out/ctw
java -cp "lib/aspectjtools-1.9.22.jar:lib/aspectjrt-1.9.22.jar"   org.aspectj.tools.ajc.Main   -17 -classpath "lib/aspectjrt-1.9.22.jar"   -showWeaveInfo -d out/ctw   src/demo/Main.java src/demo/TraceAspect.aj
java -cp "out/ctw:lib/aspectjrt-1.9.22.jar" demo.Main
```

Trên Windows PowerShell, lệnh tương ứng (đã có sẵn thư mục `out/ctw`):

```powershell
New-Item -ItemType Directory -Force out/ctw | Out-Null
java -cp "lib/aspectjtools-1.9.22.jar;lib/aspectjrt-1.9.22.jar" org.aspectj.tools.ajc.Main -17 -classpath "lib/aspectjrt-1.9.22.jar" -showWeaveInfo -d out/ctw src/demo/Main.java src/demo/TraceAspect.aj
java -cp "out/ctw;lib/aspectjrt-1.9.22.jar" demo.Main
```

**Standard output của ứng dụng** (không tính thông báo dệt trong bước `ajc`):

```text
advice before work
business work
```

Giải thích: `ajc` nhận **cả nguồn nghiệp vụ lẫn aspect** trong cùng phép biên dịch và tạo class đã weave ở `out/ctw`. Khi chạy `demo.Main`, không cần `-javaagent` bởi advice đã nằm trong bytecode đầu ra. Thông báo `-showWeaveInfo` của bước build giúp chứng minh weaver đã chọn join point mong muốn; dạng văn bản chi tiết có thể thay đổi theo phiên bản.

**Phép đối chứng:** nếu chỉ chạy `javac -d out/plain src/demo/Main.java` rồi chạy `java -cp out/plain demo.Main` thì output chỉ còn `business work`. Đây là hai artifact khác nhau, không phải pointcut tự thay đổi.

### 5.3. Phân biệt compile-time weaving và post-compile weaving

Cũng với hai file nguồn trên, có thể tách biên dịch Java trước, biên dịch aspect riêng, rồi weave binary:

```bash
mkdir -p out/plain out/aspects out/woven
javac -d out/plain src/demo/Main.java

java -cp "lib/aspectjtools-1.9.22.jar:lib/aspectjrt-1.9.22.jar"   org.aspectj.tools.ajc.Main -17   -classpath "out/plain:lib/aspectjrt-1.9.22.jar"   -d out/aspects src/demo/TraceAspect.aj

java -cp "lib/aspectjtools-1.9.22.jar:lib/aspectjrt-1.9.22.jar"   org.aspectj.tools.ajc.Main -17   -classpath "out/plain:out/aspects:lib/aspectjrt-1.9.22.jar"   -inpath out/plain -aspectpath out/aspects   -d out/woven -showWeaveInfo

java -cp "out/woven:out/aspects:lib/aspectjrt-1.9.22.jar" demo.Main
```

**Output ứng dụng mong đợi vẫn là:**

```text
advice before work
business work
```

Ở ví dụ PCW, `out/plain` là input cho `-inpath`; `out/aspects` là binary aspects dùng qua `-aspectpath`; `out/woven` là class sau khi biến đổi. Không sử dụng `out/plain` trong lệnh chạy cuối. Trên Windows, thay dấu `:` phân tách path bằng `;` trong những tham số classpath, giữ các tùy chọn khác như cũ.

Đầu vào `-inpath` có thể là JAR đã biên dịch, không chỉ thư mục. Trong build thực tế, **không nên đưa cùng một class từ nhiều input vào weaver**, hay dùng thư mục `-d` lại làm `-inpath` ở lượt build sau: có thể gây weaving lặp, xung đột hoặc đầu ra khó tái hiện.

## 6. Khai báo Maven trong lifecycle build

Maven đặt trọng tâm vào việc gắn các goal biên dịch/weaving vào các phase được chạy bởi `mvn clean test` và `mvn package`. Ví dụ sau là **một POM cơ bản đầy đủ** cho dự án Java 17 chứa mã nguồn Java và file `.aj`; các phiên bản dependency được cố định minh họa để có thể tái hiện.

Tổ chức lại code ở phần 5:

```text
aop-maven-demo/
├── pom.xml
└── src/
    └── main/
        ├── java/demo/Main.java
        └── aspect/demo/TraceAspect.aj
```

**`pom.xml`**:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>demo</groupId>
  <artifactId>aop-maven-demo</artifactId>
  <version>1.0.0</version>

  <properties>
    <maven.compiler.release>17</maven.compiler.release>
  </properties>

  <dependencies>
    <dependency>
      <groupId>org.aspectj</groupId>
      <artifactId>aspectjrt</artifactId>
      <version>1.9.22</version>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>dev.aspectj</groupId>
        <artifactId>aspectj-maven-plugin</artifactId>
        <version>1.14.1</version>
        <configuration>
          <complianceLevel>17</complianceLevel>
          <aspectDirectory>src/main/aspect</aspectDirectory>
          <showWeaveInfo>true</showWeaveInfo>
        </configuration>
        <executions>
          <execution>
            <goals>
              <goal>compile</goal>
              <goal>test-compile</goal>
            </goals>
          </execution>
        </executions>
        <dependencies>
          <dependency>
            <groupId>org.aspectj</groupId>
            <artifactId>aspectjtools</artifactId>
            <version>1.9.22</version>
          </dependency>
        </dependencies>
      </plugin>
    </plugins>
  </build>
</project>
```

Chạy:

```bash
mvn clean package
```

Lưu ý triển khai:

- Plugin `dev.aspectj:aspectj-maven-plugin` chạy `ajc` theo mục tiêu `compile` và `test-compile`. `aspectjtools` là **plugin dependency** để plugin dùng đúng compiler; `aspectjrt` là **project dependency** vì mã đã dệt có thể cần type runtime của AspectJ.
- Dùng `complianceLevel` **hoặc** cấu hình `source`/`target` phù hợp; không chồng nhiều chỉ tiêu bất nhất. Với mã Java 17, phiên bản compiler chạy build cũng cần hỗ trợ Java 17.
- Nguồn Java và aspect phải đặt đúng directory plugin được cấu hình. Nếu có module chứa binary aspect riêng, phụ thuộc aspect và cấu hình weaving tại module tiêu thụ phải được khai báo tường minh; chỉ thêm `aspectjrt` không đồng nghĩa áp dụng những aspect đó.
- Nếu có cả bộ biên dịch Java mặc định và `ajc` trong cùng lifecycle, cần hiểu thứ tự và bảo đảm **artifact cuối** chứa class đã weave. Kiểm tra `target/classes` và JAR sau `mvn clean package`; đừng chỉ thấy “BUILD SUCCESS” rồi suy ra advice đã chạy.
- `test-compile` dành cho nguồn test và weaving test nếu cần. Một phép thử integration cần thực thi chính class sau weave; nếu các test nạp output ở nơi khác, có thể cho kết quả giả.

Điều chỉnh phiên bản plugin/compiler dựa trên tài liệu và compatibility matrix đang áp dụng cho hệ thống; không nên vô điều kiện sao chép một cấu hình được viết cho Java level khác.

## 7. Khai báo Gradle: mô hình tác vụ và output

Gradle thường cần một **task rõ ràng** để chạy compiler/weaver, sau đó nối nó vào graph `compile → weave → test/package`. Một mô hình phối hợp tốt:

```text
compileJava ------------------+
                              v
                          weaveMain
                              |
                     +--------+---------+
                     v                  v
               testWovenClasses      wovenJar
                                        |
                                        v
                                    publish
```

Đặc điểm quan trọng: Gradle có thể coi `compileJava` là producer của `build/classes/java/main`, trong khi `weaveMain` phải có **thư mục output riêng** `build/woven/main`. Đừng viết đè ngược trở lại output của `compileJava` nếu task tracking/cache đang giả định output đó không thay đổi.

Ví dụ **khung cấu hình Gradle Groovy DSL** cho *binary weaving của class đã biên dịch* (giả sử thư viện chứa aspect binary được cấu hình riêng):

```groovy
plugins {
    id 'java'
}

repositories {
    mavenCentral()
}

configurations {
    ajcTools
    binaryAspects
}

dependencies {
    implementation 'org.aspectj:aspectjrt:1.9.22'
    ajcTools 'org.aspectj:aspectjtools:1.9.22'
    binaryAspects files('libs/aspects.jar')
}

def wovenDirectory = layout.buildDirectory.dir('woven/main')

tasks.register('weaveMain', JavaExec) {
    dependsOn tasks.named('compileJava')
    classpath = configurations.ajcTools
    mainClass = 'org.aspectj.tools.ajc.Main'

    doFirst {
        def output = wovenDirectory.get().asFile
        output.mkdirs()
        def plainClasses = tasks.named('compileJava').get()
            .destinationDirectory.get().asFile
        args '-17',
             '-classpath', sourceSets.main.compileClasspath.asPath,
             '-inpath', plainClasses.absolutePath,
             '-aspectpath', configurations.binaryAspects.asPath,
             '-d', output.absolutePath,
             '-showWeaveInfo'
    }
}

tasks.register('wovenJar', Jar) {
    dependsOn tasks.named('weaveMain')
    archiveClassifier = 'woven'
    from(wovenDirectory)
    from(sourceSets.main.output.resourcesDir)
}
```

Đây là **khung dành cho dự án đã có** file `libs/aspects.jar` được biên dịch riêng bằng AspectJ; nó không tự tạo file đó. Phải thêm aspect JAR vào dependency classpath phù hợp nếu ứng dụng cần tham chiếu các type trong JAR. Một pipeline multi-project nên thay đường dẫn file bằng dependency project/artifact có version kiểm soát được.

Sau khi gắn các task, chạy:

```bash
gradle clean wovenJar
```

Kiểm tra JAR có classifier `woven` chứa **class từ `build/woven/main`**, không vô tình phát hành JAR mặc định chứa class chưa dệt. Trong dự án thực, thêm task test chạy trên woven classes và dependency classpath cần thiết. Với task muốn được cache chuẩn, khai báo `inputs`/`outputs` tương ứng, tránh tạo tham số ngoài task action làm Gradle không nhìn thấy dependency, và không dùng cùng directory cho cả input lẫn output. Mẫu khung trên cố tình tách `wovenJar` để phân biệt rõ artifact đã weave với artifact Java mặc định.

Nếu dùng plugin Gradle ngoài thay vì gọi `ajc` trực tiếp, hãy kiểm tra plugin được duy trì, hỗ trợ phiên bản Gradle/JDK đang dùng và thực sự nối task weaving vào `test`, `jar`, `publish`. **Thấy dependency AspectJ trong `build.gradle` không bảo đảm weaving được chạy.**

## 8. Load-time weaving: khai báo ở build và ở JVM

LTW không dệt class trong quá trình build thông thường. Build cần cung cấp ba thứ: **application classes**, **aspect classes đã biên dịch**, và **`META-INF/aop.xml`** ở runtime classpath; môi trường khởi động JVM cần thêm weaving agent.

Ví dụ cấu hình **`src/main/resources/META-INF/aop.xml`** cho một aspect đã được biên dịch với tên đầy đủ `demo.TraceAspect`:

```xml
<!DOCTYPE aspectj PUBLIC
  "-//AspectJ//DTD//EN"
  "https://www.eclipse.org/aspectj/dtd/aspectj.dtd">
<aspectj>
  <aspects>
    <aspect name="demo.TraceAspect"/>
  </aspects>
  <weaver options="-verbose -showWeaveInfo">
    <include within="demo..*"/>
  </weaver>
</aspectj>
```

*Đây là đoạn cấu hình*, không phải chương trình chạy độc lập. Cần bảo đảm file được đóng gói dưới đường dẫn chính xác `META-INF/aop.xml` và aspect class `demo.TraceAspect` được nạp qua classloader mà weaver nhìn thấy.

**Lệnh JVM minh họa** trên Linux/macOS sau khi đã chuẩn bị `app.jar`, `aspects.jar`, runtime và agent đúng phiên bản:

```bash
java -javaagent:lib/aspectjweaver-1.9.22.jar   -cp "app.jar:aspects.jar:lib/aspectjrt-1.9.22.jar"   demo.Main
```

Môi trường Windows dùng dấu `;` cho `-cp`. Tên JAR trong ví dụ biểu diễn artifact đã được build/đặt vào đúng nơi; chỉ gắn agent không tự tạo `app.jar`, `aspects.jar` hoặc `aop.xml`. Nếu file cấu hình nằm trong `aspects.jar` hoặc `app.jar` và được đóng gói đúng cấu trúc, weaver mới có thể đọc nó.

Các ràng buộc LTW đặc biệt quan trọng:

- **Agent phải được gắn trước khi các lớp cần dệt được định nghĩa.** Khai báo aspect sau khi classloader đã load lớp có thể khiến join point bị bỏ lỡ.
- **Classloader xác định phạm vi nhìn thấy.** Weaver không tự dệt các lớp đã do classloader cha định nghĩa.
- **Tách cấu hình build với runtime.** Maven/Gradle tải `aspectjweaver` vào dependency tree không có nghĩa lệnh `java` cuối cùng có `-javaagent`.
- **Giữ CTW/PCW và LTW có chủ đích.** Nếu đưa artifact đã weave vào môi trường LTW, phải hiểu reweaving và bảo đảm cấu hình không dệt trùng ngoài thiết kế.
- **Đóng gói và triển khai đồng bộ.** Docker entrypoint, lệnh start service, test launcher và staging/prod phải nhận cùng agent, aspect JAR, classpath và cấu hình.

Khi điều tra LTW, bật `-verbose` và `-showWeaveInfo` trên môi trường tái hiện có kiểm soát, ghi danh sách classloader và kiểm tra file `aop.xml` thực sự có trên classpath.

## 9. Thiết kế pipeline build có thể tái hiện

Một pipeline đáng tin cậy có các bước theo quan hệ phụ thuộc:

1. **Resolve phiên bản:** khóa JDK toolchain, `aspectjtools`, `aspectjrt`, `aspectjweaver` (nếu có), plugin và các aspect library. Đảm bảo version nâng cấp được kiểm soát.
2. **Compile aspect và Java:** CTW cho nguồn do mình sở hữu, hoặc `javac` rồi chuẩn bị binary aspects để PCW.
3. **Weave:** thực thi task từ dữ liệu đầu vào được xác định; tạo woven output vào một nơi riêng, ghi rõ diagnostics.
4. **Test trên woven output:** chạy unit/integration test với classpath chứa artifact đúng và runtime dependencies. Nếu dùng LTW, test phải truyền agent và file cấu hình tương đương production.
5. **Package:** ưu tiên đóng gói duy nhất artifact đã xác nhận có weaving, hoặc đóng gói plain artifact cùng yêu cầu LTW được khai báo rõ.
6. **Publish/deploy:** xuất bản hash, version, artifact coordinates và hướng dẫn JVM flags; phát hành cùng chính file được test.
7. **Smoke test sau triển khai:** kiểm tra một join point có giá trị đại diện tạo advice đúng một lần, và một negative case không bị bắt nhầm.

Những lựa chọn kiến trúc cần cân nhắc:

- **Source code sẵn có:** CTW thường rõ ràng nhất nếu aspect cần inter-type declarations hoặc mã Java dựa trực tiếp vào các thành viên aspect thêm vào.
- **Thư viện bên ngoài chỉ có binary:** PCW thích hợp khi được phép biến đổi class/JAR; chú ý giấy phép, chữ ký JAR và provenance của artifact mới.
- **Muốn thay phạm vi weave theo môi trường:** LTW cung cấp cấu hình lúc load nhưng tăng yêu cầu về JVM flags/classloader và debugging.
- **Chỉ cần chặn API boundary:** proxy/interception có thể đủ, không cần `ajc`; phải chấp nhận giới hạn lời gọi không qua proxy.

Với dự án nhiều module, module chứa aspect phải được build trước module dùng nó; tuy nhiên chỉ việc “module aspect đã build” chưa khiến module nghiệp vụ được weave. Nối dependency **và** weaving task/goal ở nơi tiêu thụ, rồi kiểm tra bytecode của module đó.

## 10. Chứng minh build thực sự đã dệt aspect

Không dùng một mình exit code 0 hoặc dòng “BUILD SUCCESS” làm bằng chứng. Cần kết hợp:

| Bằng chứng | Ý nghĩa | Giới hạn |
| --- | --- | --- |
| Log `-showWeaveInfo` | Compiler/weaver cho thấy join point nào nhận advice | Log có thể tắt, và lời match chưa chứng minh artifact đúng đã được chạy |
| `jar tf app.jar` | Xác định class/resource có nằm trong JAR phát hành | Không xác định chính xác advice đã được weave chỉ từ tên class |
| `javap -c -p` hoặc so hash bytecode | Kiểm tra class đầu ra khác lớp nguyên bản như kỳ vọng | Compiler tối ưu hoặc metadata khác cũng làm bytecode khác; cần đọc kỹ |
| Smoke test positive | Đường chạy đại diện thực sự thực thi advice | Chưa bao phủ mọi signature/runtime condition |
| Smoke test negative | Đảm bảo pointcut không rộng gây side effect ngoài phạm vi | Không thay thế kiểm thử đặc tính join point phức tạp |
| Classpath/JVM startup log | Chứng minh LTW agent/cấu hình đã khởi động | Agent bật nhưng class đã load sớm hoặc bị loại vẫn không weave |

Phép thử khói tối thiểu đối với ví dụ `demo.Main`: khi chạy woven output, chương trình in đúng hai dòng `advice before work` rồi `business work`. Khi chạy plain output, chỉ in một dòng nghiệp vụ. Hai phép thử giúp chỉ ra build đã tác động thật, thay vì lời advice ngẫu nhiên có mặt do một môi trường runtime khác.

Đối với hệ thống đang chạy, nên kiểm tra một phương thức có pointcut hẹp, không gây side effect; không chọn giao dịch thanh toán/ghi dữ liệu làm smoke test nếu chưa có môi trường cô lập.

## 11. Lỗi build và cách xử lý theo nguyên nhân

| Triệu chứng | Nguyên nhân có khả năng | Cách kiểm tra/sửa |
| --- | --- | --- |
| `javac` báo lỗi với `aspect`, `before()` | Đã đưa cú pháp `.aj` cho Java compiler thường | Dùng `ajc` cho mã AspectJ, cấu hình đúng source roots |
| `ajc` không tìm được type | Thiếu `-classpath` hoặc nguồn phụ thuộc, module build sai thứ tự | Liệt kê dependency thực và source list |
| `ajc` chạy thành công nhưng không weave | Target classes ngoài nguồn/inpath, sai pointcut, thiếu aspectpath | Kiểm tra `-showWeaveInfo` và class được xử lý |
| Chạy ứng dụng không thấy advice | Đã build woven output nhưng classpath chạy plain output | So command line, JAR được phát hành và actual loaded classes |
| Class bị weave hai lần | Pipeline có CTW rồi PCW, hoặc output cũ quay lại `-inpath` | Tách đầu vào/đầu ra, kiểm tra graph và clean build |
| Lỗi `NoClassDefFoundError` liên quan AspectJ | Runtime classpath thiếu `aspectjrt` hoặc aspect library | Gắn đúng runtime dependencies vào package/launcher |
| LTW im lặng | Không truyền `-javaagent`, thiếu `aop.xml`, agent không thấy aspect | Xem lệnh JVM thực, JAR content, verbose weaver log |
| LTW bỏ sót class | Classload trước agent/cấu hình, classloader khác, exclude pattern | Kiểm tra thời điểm load, classloader và phạm vi include/exclude |
| Test qua nhưng production sai | Test/production dùng artifact khác hoặc thiếu agent ở một môi trường | Đồng nhất test launcher, artifact hash và startup flags |
| Incremental build lúc đúng lúc sai | Không khai báo inputs/outputs cho weave task, cache cũ hoặc output đè input | Khai báo graph chuẩn, rebuild sạch để đối chiếu, sửa cache dependencies |

**Các lỗi cần tránh:** chỉ thêm một dependency rồi cho rằng đã weave; dùng `-classpath` thay `-inpath`; đưa class đầu ra đã weave vào lại bước weaving không kiểm soát; đóng gói plain classes sau khi weave thành công; bỏ qua logs warning về những join point không match; hoặc quên truyền agent khi triển khai LTW.

## 12. Checklist cho code review và CI

- [ ] Đã chọn rõ **CTW, PCW, LTW hoặc proxy interception** theo yêu cầu join point.
- [ ] Compiler và runtime đã được khóa phiên bản, hỗ trợ đúng JDK và bytecode target.
- [ ] Nguồn Java, source aspect, binary aspect và các thư viện đã gắn vào đúng classpath/source roots.
- [ ] `-inpath`, `-aspectpath`, `-d` và `-outjar` (nếu có) được sử dụng đúng nghĩa.
- [ ] Build graph có quan hệ `compile → weave → test → package` đúng hướng; không bỏ qua step weaving khi CI chạy.
- [ ] Đầu vào và đầu ra weaving không ghi đè lẫn nhau; cache/clean build dễ tái hiện.
- [ ] Class/JAR được test chính là class/JAR được phát hành; không vô tình dùng plain output.
- [ ] Có positive/negative tests chứng minh pointcut tác động đúng nơi và đúng số lần.
- [ ] Với LTW: `META-INF/aop.xml`, aspect, runtime dependency và `-javaagent` đều hiện diện khi chạy.
- [ ] Đã xem weave diagnostics và các cảnh báo quan trọng; không mặc định “BUILD SUCCESS” là “AOP SUCCESS”.
- [ ] Đã kiểm tra classloader, module boundary, executable JAR/container packaging khi hệ thống có chúng.

## 13. Tài liệu chính thống để đối chiếu theo phiên bản

- [Tài liệu `ajc` của Eclipse AspectJ](https://eclipse.dev/aspectj/doc/latest/devguide/ajc.html): tham số compiler, `-inpath`, `-aspectpath`, compiler level và diagnostics.
- [Load-Time Weaving của Eclipse AspectJ](https://eclipse.dev/aspectj/doc/latest/devguide/ltw.html): cấu hình `META-INF/aop.xml`, agent, classloader và phạm vi weave.
- [AspectJ Maven Plugin](https://github.com/dev-aspectj/aspectj-maven-plugin): cách chạy compiler AspectJ từ Maven, gắn compiler/runtime versions.
- [AspectJ Maven Plugin Usage](https://dev-aspectj.github.io/aspectj-maven-plugin/usage.html): goal `compile` và `test-compile`.

**Nguyên tắc thực hành:** build phải xác định **điểm weave, tập bytecode đầu vào, aspect được áp dụng và artifact chạy cuối cùng**. Khi bốn yếu tố đó rõ ràng, việc bổ sung AOP trở thành một bước có thể kiểm thử và tái hiện, thay vì phụ thuộc vào một thiết lập IDE hoặc một JVM flag dễ bị bỏ sót.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-integration-deployment-002">Tách mã aspect, cấu hình và thư viện dùng chung</a>

<details>
<summary>Xem chi tiết</summary>

# Tách mã aspect, cấu hình và thư viện dùng chung

## 1. Bài toán: aspect tái sử dụng được nhưng phải có ranh giới rõ

Trong lập trình hướng khía cạnh (Aspect-Oriented Programming, AOP), **aspect** gom một mối quan tâm xuyên suốt nhiều thành phần, chẳng hạn đo thời gian, ghi dấu vết, kiểm tra truy cập, giới hạn số lần thử hoặc kiểm toán. Khi một dự án lớn dần, việc đặt toàn bộ advice, biểu thức pointcut, tham số cấu hình, mã kích hoạt weaving/proxy và tiện ích chung vào cùng một gói khiến sửa một chính sách có thể ảnh hưởng đến nhiều dịch vụ.

Mục tiêu của việc tách là thiết lập ranh giới **ai định nghĩa hợp đồng, ai hiện thực chính sách, ai cấu hình chính sách và ai gắn chính sách vào nghiệp vụ**. Không phải cứ chia ra nhiều thư mục là đã tách đúng: cần nhìn vào **chiều phụ thuộc**, **quyền sở hữu cấu hình**, **thời điểm áp dụng AOP** và **kiểm thử tích hợp**.

Sau bài học, bạn có thể:

- Phân biệt **mã aspect/advice**, **cấu hình** và **thư viện dùng chung** về trách nhiệm và vòng đời.
- Xác định nơi đặt pointcut, precedence, cơ chế proxy/weaving và composition root.
- Thiết kế phụ thuộc một chiều để nghiệp vụ không phải biết advice đang tồn tại.
- Đóng gói, lập phiên bản và kiểm thử riêng từng phần mà vẫn xác minh được đường gọi đã được can thiệp.
- Áp dụng mô hình cụ thể với Java 17+, thư viện hợp đồng nhỏ và một ứng dụng tự lắp ráp chuỗi around advice.

Phạm vi là **AOP tổng quát**. Không có một chuẩn duy nhất áp đặt cấu trúc dự án hay thuật toán precedence cho mọi công cụ AOP. Ví dụ dùng **JDK dynamic proxy** để minh họa việc tách thành phần có thể chạy độc lập; phần tích hợp weaving khi biên dịch hoặc khi tải lớp được phân tích riêng ở các mục sau.

## 2. Bốn ranh giới trách nhiệm

| Ranh giới | Chứa gì? | Không nên chứa gì? | Ai thay đổi? |
| --- | --- | --- | --- |
| **Hợp đồng AOP dùng chung** | Giao diện continuation/invocation, advice API, kiểu dữ liệu context, ngoại lệ hợp đồng nếu cần. | Pointcut gắn với domain cụ thể; biến cấu hình của từng ứng dụng; secret. | Nhóm sở hữu thư viện công cụ. |
| **Mã aspect/chính sách** | Logic của tracing, authorization, audit, retry; xử lý trước/sau/quanh join point. | Đọc trực tiếp file cấu hình của ứng dụng; tự khởi động engine; biết cách triển khai từng môi trường. | Nhóm sở hữu chính sách xuyên suốt. |
| **Cấu hình và quy tắc áp dụng** | Bật/tắt tính năng được phép, giá trị timeout, selector/pointcut, thứ tự precedence, ánh xạ chính sách với join point. | Mã nghiệp vụ, thuật toán advice, phụ thuộc ngầm vào global mutable state. | Ứng dụng hoặc lớp triển khai, trong phạm vi quyền được cấp. |
| **Composition root / lớp tích hợp** | Tạo aspect, inject dependency, chọn proxy/weaver, kết nối target với advice, kiểm tra cấu hình. | Logic xử lý nghiệp vụ và logic chuyên sâu của từng aspect. | Ứng dụng sử dụng các thư viện. |

Một **thư viện dùng chung** không đồng nghĩa với “nơi bỏ mọi mã không biết đặt ở đâu”. Chỉ nên chia sẻ những khái niệm có hợp đồng ổn định và thực sự được nhiều module dùng. Ví dụ, giao diện AroundAdvice có thể thuộc lõi chung, còn quy tắc “chỉ ghi audit cho đơn hàng đã thanh toán” thuộc chính sách hoặc ứng dụng sở hữu nghiệp vụ đó.

**Nguyên tắc chủ đạo:** target nghiệp vụ không phụ thuộc vào lớp aspect cụ thể. Ứng dụng biết những aspect nào cần dùng khi lắp ghép; aspect phụ thuộc vào API tối thiểu và các dịch vụ được inject, không đi ngược vào entry point của ứng dụng.

## 3. Kiến trúc phụ thuộc một chiều

Một cách tổ chức ở cấp module có thể là:

~~~text
                    host-app
                     /  |  \
                    v   v   v
          aop-aspects  aop-engine   app-config
                 \       /          (ứng dụng sở hữu)
                  v     v
                aop-contract

    domain-business  <--- được host-app lắp với advice
          |
          +--- không tham chiếu aop-aspects hay aop-engine
~~~

Sơ đồ diễn tả **quan hệ sử dụng**, không yêu cầu mọi dự án phải xuất bản bốn artifact riêng. Với một chương trình nhỏ, các ranh giới này có thể là những package trong một artifact; khi cần tái sử dụng nhiều nơi, mới cân nhắc tách thành các module/JAR độc lập.

### 3.1. aop-contract: API tối thiểu

Lõi định nghĩa thao tác continuation như proceed và hợp đồng quanh join point. Nếu cần metadata về phương thức, tham số, đích, context, danh tính yêu cầu, thiết kế các kiểu dữ liệu đó **bất biến hoặc có quy tắc mutability rõ**, tránh làm API trở thành “túi” chứa mọi dữ liệu. Không ràng buộc hợp đồng vào một engine/proxy cụ thể nếu mục tiêu là tái sử dụng.

**Lưu ý:** mỗi lần gọi proceed có thể thực thi phần còn lại của chuỗi. Một số advice quanh join point cho phép gọi nhiều lần hoặc bỏ qua; việc đó phải được thể hiện trong hợp đồng, tài liệu và phép thử.

### 3.2. aop-aspects: chính sách độc lập với bootstrap

Aspect hiện thực một mối quan tâm rõ ràng. Ví dụ tracing chỉ quyết định **sự kiện nào được ghi**, định dạng nào và xử lý lỗi của việc ghi; nó không nên tự đọc biến môi trường hoặc quyết định lớp nghiệp vụ nào cần được bọc. Kiểm tra quyền chỉ nhận nguồn kiểm tra quyền/phán quyết thông qua giao diện; không nên phụ thuộc vào implementation của ứng dụng khác.

Chia một aspect quá lớn thành những advice có trách nhiệm dễ kiểm thử, nhưng tránh tách tùy tiện đến mức một quy tắc phải chạm hàng chục artifact. Dùng các lớp phụ trợ có thể tái sử dụng trong chính thư viện aspect nếu chúng chỉ phục vụ nội bộ chính sách.

### 3.3. app-config: đầu vào được kiểm tra và có kiểu rõ

Cấu hình quyết định chính sách nào hoạt động, với ngưỡng nào, thứ tự ưu tiên nào, và join point nào được chọn. Lớp chuyển đổi cấu hình chịu trách nhiệm đọc nguồn (tham số chạy, tệp, biến môi trường hoặc nguồn cấu hình được hỗ trợ), kiểm tra schema và tạo **đối tượng cấu hình có kiểu**. Advice nhận đối tượng hoặc những giá trị đã kiểm tra.

Một tùy chọn cấu hình không hợp lệ nên được báo rõ khi khởi động (*fail fast*) nếu nó có thể khiến chính sách bảo vệ không hoạt động. Không tự động chuyển mọi lỗi parse thành giá trị mặc định thuận tiện.

### 3.4. host-app: composition root và entry point

Composition root là chỗ duy nhất lắp các mảnh: tạo target, chọn aspect instance, sắp xếp thứ tự, kết nối với cơ chế interception rồi cấp đối tượng hoàn chỉnh cho caller. Nó có quyền biết cả domain lẫn thư viện AOP. Nhờ vậy mã nghiệp vụ không phải gọi thủ công “tracing.before” và “tracing.after” ở mỗi phương thức.

## 4. Ranh giới aspect, pointcut và cấu hình precedence

Ba khái niệm dễ bị trộn là:

1. **Advice:** phải làm gì trước/quanh/sau join point, kể cả khi target lỗi?
2. **Pointcut/selector:** join point nào được áp dụng advice?
3. **Precedence/order:** nếu nhiều advice cùng áp dụng, ai bao ngoài, ai bao trong?

Một aspect có thể mang pointcut tĩnh ở cấp mã, nhưng các quy tắc lựa chọn **phụ thuộc ứng dụng** thường nên ở lớp tích hợp/cấu hình (nếu engine cho phép). Ví dụ thư viện tracing biết cách ghi sự kiện; ứng dụng chọn chỉ tracing các lời gọi dịch vụ công khai, bỏ qua các phương thức nội bộ và chính code ghi log để tránh vòng lặp.

Trong một chuỗi around:

~~~text
caller
  TraceAdvice.enter
    AccessAdvice.check
      target.execute()
    AccessAdvice.return
  TraceAdvice.exit
caller nhận kết quả
~~~

Trace ở **ngoài** Access nhìn thấy cả lỗi bị Access chặn; Trace ở **trong** Access có thể không chạy nếu truy cập bị từ chối. Cả hai cách có thể đúng tùy mục tiêu; không nên dựa vào “tên lớp theo alphabet” hay “thứ tự đăng ký tình cờ” để suy luận precedence.

**Hợp đồng thứ tự** phải được mô tả và kiểm chứng bằng event trace: ghi advice enter, proceed, advice exit/catch, target enter. Khi hệ AOP áp dụng thứ tự theo annotation, precedence declaration, cấu hình DSL hoặc thứ tự đăng ký, phải xác minh theo **tài liệu và phiên bản engine thực tế**. Không áp đặt quy tắc của bộ chặn tự viết dưới đây cho engine khác.

## 5. Ví dụ đa tệp Java 17+: tách hợp đồng, mã aspect và cấu hình

Ví dụ nhỏ này không cần thư viện bên thứ ba. Nó sử dụng JDK dynamic proxy cho một interface nghiệp vụ và dùng các package khác nhau để mô phỏng ranh giới module.

**Cấu trúc tệp:**

~~~text
demo/
├── api/
│   ├── Invocation.java
│   └── AroundAdvice.java
├── core/
│   └── AdviceChain.java
├── aspect/
│   ├── TraceAdvice.java
│   └── AccessAdvice.java
├── config/
│   └── AspectSettings.java
└── app/
    ├── OrderService.java
    └── Main.java
~~~

Tám tệp dưới đây là **mã đầy đủ**. Trong ví dụ chúng biên dịch cùng classpath để dễ học; khi tách thành artifact khác nhau, cần giữ đúng hướng phụ thuộc đã nêu. Thứ tự danh sách advice được chọn rõ: phần tử đầu tiên là vòng **ngoài cùng**.

### 5.1. API chung: demo/api/Invocation.java

~~~java
package demo.api;

@FunctionalInterface
public interface Invocation {
    Object proceed() throws Throwable;
}
~~~

**Invocation** đóng vai trò continuation. Nó không biết domain OrderService, cách lấy cấu hình hoặc cơ chế proxy.

### 5.2. API chung: demo/api/AroundAdvice.java

~~~java
package demo.api;

@FunctionalInterface
public interface AroundAdvice {
    Object apply(Invocation next) throws Throwable;
}
~~~

Hợp đồng đơn giản nhận một continuation và có thể trả kết quả hoặc ném lỗi. Ví dụ cố ý giữ API nhỏ; các thư viện AOP thực tế có thể cần InvocationContext chứa join point, Method, tham số và metadata, đồng thời cần hợp đồng rõ về xử lý nhiều lần proceed.

### 5.3. Lõi chặn lời gọi: demo/core/AdviceChain.java

~~~java
package demo.core;

import demo.api.AroundAdvice;
import demo.api.Invocation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;

public final class AdviceChain {
    private AdviceChain() {}

    public static <T> T wrap(Class<T> contract, T target,
                             List<AroundAdvice> advices) {
        if (!contract.isInterface() || !contract.isInstance(target)) {
            throw new IllegalArgumentException("target must implement interface");
        }
        return contract.cast(Proxy.newProxyInstance(
                contract.getClassLoader(), new Class<?>[]{contract},
                (proxy, method, args) -> {
                    Invocation current = () -> {
                        try {
                            return method.invoke(target, args);
                        } catch (InvocationTargetException wrapper) {
                            throw wrapper.getCause();
                        }
                    };
                    for (int i = advices.size() - 1; i >= 0; i--) {
                        AroundAdvice layer = advices.get(i);
                        Invocation next = current;
                        current = () -> layer.apply(next);
                    }
                    return current.proceed();
                }));
    }
}
~~~

Phần lõi dựng chain bằng cách đi ngược danh sách, nên advice thứ nhất được thực thi trước khi vào những advice còn lại và nhận kết quả sau cùng. Target được gọi qua reflection; InvocationTargetException được mở để advice nhìn thấy exception gốc.

Đây là ví dụ **chặn qua interface proxy**, chưa phải một engine AOP hoàn chỉnh. Một implementation sản xuất phải xác định cách xử lý các phương thức đặc biệt như equals/hashCode/toString, kiểm soát kiểu trả về, checked exception, tham số, phương thức mặc định và giới hạn của proxy. Đặc biệt, JDK proxy chỉ can thiệp lời gọi **thực sự đi qua proxy**; nó không tự weave mọi method execution trong chương trình.

### 5.4. Aspect tracing: demo/aspect/TraceAdvice.java

~~~java
package demo.aspect;

import demo.api.AroundAdvice;
import demo.api.Invocation;

public final class TraceAdvice implements AroundAdvice {
    @Override
    public Object apply(Invocation next) throws Throwable {
        System.out.println("TRACE enter");
        try {
            Object result = next.proceed();
            System.out.println("TRACE exit");
            return result;
        } catch (Throwable failure) {
            System.out.println("TRACE error=" + failure.getClass().getSimpleName());
            throw failure;
        }
    }
}
~~~

TraceAdvice chỉ biết hợp đồng invocation, không biết OrderService hay tham số dòng lệnh. Trong ứng dụng thật, thay System.out bằng một abstraction xuất sự kiện/log và quy định cách xử lý khi hệ ghi log thất bại; không được làm mất exception nghiệp vụ một cách ngẫu nhiên.

### 5.5. Aspect kiểm tra điều kiện: demo/aspect/AccessAdvice.java

~~~java
package demo.aspect;

import demo.api.AroundAdvice;
import demo.api.Invocation;
import java.util.Objects;
import java.util.function.BooleanSupplier;

public final class AccessAdvice implements AroundAdvice {
    private final BooleanSupplier allowed;

    public AccessAdvice(BooleanSupplier allowed) {
        this.allowed = Objects.requireNonNull(allowed);
    }

    @Override
    public Object apply(Invocation next) throws Throwable {
        if (!allowed.getAsBoolean()) {
            throw new SecurityException("Denied");
        }
        System.out.println("CHECK allowed");
        return next.proceed();
    }
}
~~~

AccessAdvice nhận một dependency cung cấp quyết định qua BooleanSupplier. Đây chỉ là **điều kiện giả lập để chứng minh wiring**, không phải hệ phân quyền thực tế; với authorization thật, cần danh tính tin cậy, ngữ cảnh yêu cầu, chính sách mặc định từ chối, xử lý lỗi nguồn quyền và kiểm thử đường đi không được bảo vệ.

### 5.6. Cấu hình có kiểu: demo/config/AspectSettings.java

~~~java
package demo.config;

public record AspectSettings(boolean tracing, boolean allowRequest) {
    public static AspectSettings parse(String[] args) {
        boolean tracing = true;
        boolean allowRequest = true;
        for (String arg : args) {
            switch (arg) {
                case "--no-trace" -> tracing = false;
                case "--deny" -> allowRequest = false;
                default -> throw new IllegalArgumentException("Unknown option: " + arg);
            }
        }
        return new AspectSettings(tracing, allowRequest);
    }
}
~~~

Record chứa giá trị bất biến sau khi parse. Hai tham số của ví dụ giúp tạo các kịch bản kiểm tra có đầu ra xác định. Giá trị allowRequest là **dữ liệu giả lập trong demo**, không phải cấu hình được khuyến nghị để tắt một cơ chế bảo vệ khi triển khai thực tế.

Cấu hình được tạo một lần ở composition root và truyền vào constructor/lambda của aspect. Advice không tự gọi System.getenv hay đọc tệp mỗi lần chạy. Nếu ứng dụng hỗ trợ đổi cấu hình lúc chạy, cần chính sách áp dụng snapshot nhất quán và một thiết kế reload an toàn riêng.

### 5.7. Hợp đồng nghiệp vụ: demo/app/OrderService.java

~~~java
package demo.app;

public interface OrderService {
    String create(String sku);
}
~~~

Hợp đồng nghiệp vụ không import bất kỳ mã aspect nào.

### 5.8. Ứng dụng lắp ráp: demo/app/Main.java

~~~java
package demo.app;

import demo.api.AroundAdvice;
import demo.aspect.AccessAdvice;
import demo.aspect.TraceAdvice;
import demo.config.AspectSettings;
import demo.core.AdviceChain;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        AspectSettings settings = AspectSettings.parse(args);
        OrderService target = sku -> {
            System.out.println("BUSINESS order=" + sku);
            return "ok:" + sku;
        };

        List<AroundAdvice> advices = new ArrayList<>();
        if (settings.tracing()) {
            advices.add(new TraceAdvice());
        }
        advices.add(new AccessAdvice(settings::allowRequest));
        OrderService service = AdviceChain.wrap(OrderService.class, target, advices);

        try {
            System.out.println("RESULT " + service.create("BOOK-1"));
        } catch (SecurityException denied) {
            System.out.println("RESULT blocked");
        }
    }
}
~~~

Main là **composition root**: chỉ Main biết mình dùng cả TraceAdvice, AccessAdvice, AspectSettings, AdviceChain và OrderService. Target nghiệp vụ vẫn chỉ xử lý SKU. Đăng ký theo thứ tự **TRACE ngoài ACCESS**, vì thế khi ACCESS từ chối, TRACE nhìn thấy và ghi exception.

### 5.9. Biên dịch và chạy

Chạy trong thư mục **chứa thư mục demo/**. Lệnh có danh sách tệp tường minh nên dùng được trong shell Bash lẫn PowerShell, với JDK 17+:

~~~bash
javac -d out demo/api/Invocation.java demo/api/AroundAdvice.java demo/core/AdviceChain.java demo/aspect/TraceAdvice.java demo/aspect/AccessAdvice.java demo/config/AspectSettings.java demo/app/OrderService.java demo/app/Main.java
java -cp out demo.app.Main
~~~

**Stdout chế độ mặc định, đã kiểm chứng với JDK 21:**

~~~text
TRACE enter
CHECK allowed
BUSINESS order=BOOK-1
TRACE exit
RESULT ok:BOOK-1
~~~

Tắt riêng tracing để thấy việc điều khiển thành phần nằm ở cấu hình và composition root:

~~~bash
java -cp out demo.app.Main --no-trace
~~~

~~~text
CHECK allowed
BUSINESS order=BOOK-1
RESULT ok:BOOK-1
~~~

Thử nhánh từ chối giả lập để thấy TRACE nằm ngoài ACCESS:

~~~bash
java -cp out demo.app.Main --deny
~~~

~~~text
TRACE enter
TRACE error=SecurityException
RESULT blocked
~~~

Ba kết quả cho thấy **cùng một business target**, thay đổi chính sách và cấu hình ở bên ngoài target, không cần chèn lời gọi tracing/authorization trong mã create(). Đây là lợi ích thiết kế cốt lõi của tách mã AOP.

**Giới hạn của ví dụ:** không có pointcut matcher tổng quát, bộ giải quyết precedence của engine, interception của field access, constructor, hoặc weaving bytecode. Wrapper trong ví dụ áp dụng chain cho mọi phương thức được gọi qua interface proxy; trong một hệ triển khai thực, phải ràng buộc đúng tập join point và ngăn interceptor tác động vào những lời gọi không phù hợp.

## 6. Phân ranh giới khi chuyển từ package sang JAR/module độc lập

Ví dụ được biên dịch một lần cho dễ thực hành. Khi nhiều ứng dụng cùng sử dụng một bộ aspect, tổ chức thành artifact có thể là:

~~~text
aop-contract/     -> around advice API, invocation/context model
aop-engine/       -> proxy adapter hoặc tích hợp weaving, phụ thuộc contract
aop-aspects/      -> trace/audit/retry implementations, phụ thuộc contract
host-app/         -> config, domain, entry point; phụ thuộc các phần cần dùng
~~~

**Không nhất thiết xuất bản thư viện riêng cho mỗi aspect**. Chỉ tách artifact khi có nhu cầu vòng đời độc lập, khách hàng sử dụng chung hoặc ranh giới trách nhiệm rõ. Nếu aop-aspects cùng thay đổi và xuất bản với host-app, tách JAR có thể chỉ tăng chi phí quản lý.

Ma trận phụ thuộc mong muốn:

| Thành phần | Được tham chiếu | Tránh tham chiếu |
| --- | --- | --- |
| aop-contract | API Java ổn định, kiểu dữ liệu nền tảng | host-app, mã domain, cấu hình triển khai |
| aop-engine | aop-contract, cơ chế proxy/weaving cụ thể | chính sách nghiệp vụ riêng |
| aop-aspects | aop-contract, abstraction logging/clock/policy thích hợp | Main, domain implementation của host-app |
| host-app | API, engine, các aspect đã chọn, domain | Không có hạn chế chiều ngược vì đây là điểm lắp ráp |

Ví dụ phụ thuộc mô tả bằng Gradle Groovy DSL **chỉ để thấy chiều tham chiếu**, không phải build script hoàn chỉnh:

~~~groovy
// aop-engine/build.gradle
dependencies {
    implementation project(':aop-contract')
}

// aop-aspects/build.gradle
dependencies {
    implementation project(':aop-contract')
}

// host-app/build.gradle
dependencies {
    implementation project(':aop-contract')
    implementation project(':aop-engine')
    implementation project(':aop-aspects')
}
~~~

Mã demo trực tiếp import AroundAdvice ở host-app; khi xuất bản, hợp đồng phải được phơi bày phù hợp (chẳng hạn API dependency hoặc phụ thuộc explicit ở consumer). Không để thư viện tùy tiện kéo theo dependency transitive khiến ứng dụng vô tình có thêm một cơ chế weaving/proxy khác.

### 6.1. Ổn định hợp đồng giữa các bản phát hành

Với thư viện dùng chung, cần công bố:

- Namespace/package ổn định cho API công khai.
- Ngữ nghĩa của proceed, quy tắc số lần gọi, cách truyền ngoại lệ và giá trị trả về.
- Tính thread-safe và vòng đời được hỗ trợ của advice/context.
- Phiên bản Java tối thiểu và những artifact runtime cần thiết.
- Quy tắc deprecation, thay đổi breaking, kiểm tra compatibility.
- Phiên bản engine AOP, đặc biệt nếu API cấu hình pointcut phụ thuộc engine.

Thay chữ ký AroundAdvice hoặc đổi ý nghĩa “thứ tự đầu danh sách” có thể phá nhiều consumer dù mã vẫn biên dịch. Hãy kiểm tra source/binary compatibility của public API và hành vi bằng contract tests trước khi phát hành.

### 6.2. Quy tắc đóng gói để tránh trùng thư viện

Một ứng dụng vô tình kéo vào hai phiên bản API AOP có thể gặp lỗi liên kết class, phương thức không tồn tại hoặc khác biệt classloader. Dùng dependency graph/lockfile hay cơ chế quản lý phiên bản phù hợp để kiểm soát. Không “shade” API công khai vào nhiều JAR khác nhau nếu các bên cần cùng một kiểu AroundAdvice; có thể khiến hai lớp có tên gần giống nhưng không tương thích về identity.

## 7. Cấu hình nên nằm ở đâu?

### 7.1. Tách giá trị cấu hình khỏi logic advice

Những ví dụ cấu hình phổ biến:

| Giá trị | Người sở hữu phù hợp | Rủi ro nếu trộn vào advice |
| --- | --- | --- |
| tracingEnabled | host-app / deployment | Khó bật/tắt có kiểm soát, khó test. |
| traceSampleRate | host-app; aspect dùng số đã kiểm tra | Parse lặp lại, không nhất quán giữa các instance. |
| retryMaxAttempts | từng ứng dụng/chính sách | Một thư viện ép retry cho mọi nghiệp vụ, tăng tác dụng phụ. |
| auditSink | lớp tích hợp chọn implementation | Thư viện khó chạy trong test, phụ thuộc hạ tầng bên ngoài. |
| pointcut expression | tầng tích hợp hoặc artifact engine-specific | Thư viện chứa tên class/domain cứng, khó tái sử dụng. |
| precedence | lớp tích hợp của engine | Thay đổi thứ tự ngầm gây lệch hành vi và xử lý lỗi. |

Nên khai báo **schema**, kiểu, mặc định có chủ đích, giá trị hợp lệ, đơn vị đo, cách ưu tiên nguồn và trạng thái thiếu bắt buộc. Ví dụ: số lần retry tối đa không âm, timeout có đơn vị, biểu thức pointcut phải biên dịch hợp lệ, hoặc cấu hình kiểm soát truy cập không được rơi về “cho phép tất cả” khi parse lỗi.

### 7.2. Thứ tự ưu tiên nguồn cấu hình

Một hệ có thể lấy giá trị từ build defaults, tệp cấu hình, biến môi trường và đối số dòng lệnh. Phải **định nghĩa precedence rõ**, chẳng hạn đối số dòng lệnh ghi đè môi trường, môi trường ghi đè tệp, còn tệp ghi đè mặc định. Đây là **một ví dụ chính sách**, không phải quy tắc chung của AOP.

Tránh việc mỗi aspect tự có thuật toán chọn nguồn khác nhau. Cung cấp một bước resolve tập trung rồi tạo **cấu hình bất biến** để ghi lại snapshot khởi động. Với secret, sử dụng cơ chế cấp phát bí mật riêng, hạn chế in giá trị nhạy cảm ra log và không đóng gói secret vào JAR.

### 7.3. Cấu hình runtime thay đổi động

Một bản cấu hình bất biến được inject lúc tạo aspect sẽ không tự thay đổi chỉ vì tệp bên ngoài vừa đổi. Nếu thật sự cần reload, thiết kế cơ chế cập nhật atomically (thay toàn bộ snapshot), quy tắc cho lời gọi đang chạy, đồng bộ các instance, audit thay đổi và rollback. Không cho advice đọc file vào mỗi lần proceed vì vừa tăng overhead, vừa gây trạng thái không nhất quán trong cùng một yêu cầu.

### 7.4. Chỉ bật tính năng có thể tùy chọn an toàn

Tracing có thể là chức năng có thể tắt trong môi trường thử nghiệm; authorization/audit bắt buộc không nên bị vô hiệu hóa bằng một flag chung dễ bị dùng nhầm. Demo có tham số --deny để minh họa nhánh policy, **không đại diện một hệ thống bảo mật thực tế**. Mỗi policy cần được phân loại: bắt buộc, có thể tùy chọn, hoặc có thể thay cấu hình; cách kiểm tra khi thiếu policy tương ứng phải rõ.

## 8. Tích hợp ba cơ chế áp dụng AOP

Ranh giới mã aspect/config/shared library vẫn hữu ích, nhưng **điểm kết nối engine** khác nhau theo cơ chế.

### 8.1. Proxy interception

Composition root tạo target, xây proxy và cấp **proxy** cho caller. Một lời gọi đi trực tiếp vào target gốc có thể bỏ qua advice; một lời gọi nội bộ trên cùng instance cũng có thể không qua interception. Các cơ chế proxy khác nhau có giới hạn khác nhau về interface, method visibility, finality và đối tượng đích.

Bài kiểm tra tích hợp quan trọng là gọi qua **cùng loại reference** mà ứng dụng thật sử dụng, không chỉ gọi advice trực tiếp. Nếu test advice chạy nhưng ứng dụng không có log, kiểm tra nơi proxy được tạo, nơi reference được truyền và điểm caller thực sự dùng.

### 8.2. Compile-time weaving

Engine có thể weave các advice vào bytecode khi build. Lớp tích hợp phải sở hữu tùy chọn compiler/weaver, nguồn aspect được nạp, pointcut, precedence và thứ tự bước build. Một thư viện aspect chỉ tồn tại trên classpath **chưa đủ** để bảo đảm target đã được weave, nếu cấu hình cần một bước riêng.

Cần lưu bằng chứng build: phiên bản weaver, cấu hình, class đầu vào/đầu ra, log các join point được áp dụng và artifact được phát hành. Nếu build có nhiệm vụ weave riêng, chạy kiểm thử trên **artifact sau weaving**, không chỉ trên class trước biến đổi.

### 8.3. Load-time weaving

Weaver có thể biến đổi lớp khi tải vào runtime, thường cần bootstrap/agent hoặc integration hook do công cụ cung cấp. Chủ sở hữu cấu hình phải xác định agent và các tùy chọn khi khởi động, các classloader cần quan sát, class/path được include/exclude và trình tự load.

Một MRE chỉ dùng proxy không chứng minh rằng load-time weaving đã chạy. Kiểm tra class đã tải qua loader nào, weave xảy ra trước hay sau khi class được định nghĩa, có module access phù hợp hay không, và phương thức thực tế có được can thiệp không. Cú pháp agent, file cấu hình và quy tắc chọn join point là **đặc thù của engine**; cần theo tài liệu phiên bản được dùng.

### 8.4. Không để engine rò vào business API

Một ứng dụng có thể phải chọn engine AOP cụ thể. Tuy nhiên, nếu muốn tái sử dụng cùng logic tracing/policy giữa nhiều engine, đặt phần adapter engine-specific ở **aop-engine** hoặc các gói adapter riêng; phần chính sách thuần phụ thuộc contract trung lập. Khi hai engine có tập join point khác nhau, đừng che giấu khác biệt đó dưới một abstraction quá hứa hẹn: tài liệu hóa các khả năng thực sự hỗ trợ.

## 9. Quản lý thứ tự, trạng thái và tác dụng phụ giữa nhiều aspect

### 9.1. Thứ tự phải kiểm thử được

Giả sử có TRACE, CHECK và RETRY. Nếu RETRY nằm ngoài CHECK, phép kiểm tra có thể lặp lại mỗi attempt; nếu CHECK nằm ngoài RETRY, nó chỉ kiểm tra trước toàn bộ hoạt động retry. Nếu TRACE nằm ngoài RETRY, trace có thể đại diện cho một yêu cầu logic; nếu TRACE nằm trong RETRY, mỗi attempt có thể có trace riêng.

Không có thứ tự đúng tuyệt đối cho mọi ứng dụng. Thứ tự phù hợp phụ thuộc **hợp đồng nghiệp vụ**, tính idempotent, các ranh giới lỗi và ngữ nghĩa đo lường. Document order và dùng test có event sequence chính xác để bảo vệ.

### 9.2. Vòng đời đối tượng advice

Một instance advice có thể phục vụ nhiều lời gọi và nhiều thread. Không lưu “request hiện tại” trong trường instance dùng chung nếu chưa thiết kế cơ chế cô lập. Dùng metadata truyền theo invocation hoặc một context có vòng đời rõ; nếu dùng ThreadLocal, cần quy tắc cleanup và xử lý khi chuyển thread.

**Bất biến thường cần kiểm tra:** một yêu cầu không bị lẫn correlation id sang yêu cầu khác; một phép gọi proceed được thực hiện đúng số lần theo hợp đồng; side effect chỉ xuất hiện khi chính sách cho phép.

### 9.3. Hiệu năng và failure policy

Advice trên hot path có thể làm tăng latency ngay cả khi business method đơn giản. Tránh parse cấu hình, phản chiếu lặp lại không cần thiết, cấp phát quá nhiều, truy cập mạng đồng bộ hoặc ghi log vô hạn ở mỗi join point. Nếu dùng cache metadata, bảo đảm tính đúng với nhiều classloader và khi thay class.

Phải quy định rõ khi subsystem của aspect thất bại: ví dụ ghi metric thất bại có thể được xử lý khác kiểm soát truy cập thất bại. Không gộp mọi Throwable vào “bỏ qua lỗi để chạy tiếp”; quyết định fail-open/fail-closed cần xuất phát từ hợp đồng từng chính sách và được kiểm thử.

## 10. Chiến lược kiểm thử theo ranh giới

| Lớp kiểm thử | Cách thực hiện | Điều được chứng minh |
| --- | --- | --- |
| **Contract test** | Gọi AroundAdvice bằng Invocation giả có counter và Throwable định trước. | Advice gọi proceed/return/throw đúng hợp đồng. |
| **Unit test cấu hình** | Parse đối số hợp lệ, sai định dạng, thiếu bắt buộc; kiểm tra immutable settings. | Chính sách mặc định, xác thực đầu vào, fail-fast. |
| **Unit test aspect** | Inject BooleanSupplier, clock/sink giả; quan sát output/events. | Quy tắc tracing/check độc lập với engine. |
| **Integration test proxy** | Gọi qua proxy thật, so sánh call count/exception với target. | Wiring, thứ tự và interception qua interface hoạt động. |
| **Integration test weaving** | Build/run bằng engine và cấu hình weaving thực, kiểm tra join point. | Aspect thật sự được áp dụng vào bytecode/runtime. |
| **Compatibility test** | Consumer biên dịch/chạy với phiên bản thư viện mới và cũ được hỗ trợ. | API và ngữ nghĩa công khai vẫn tương thích. |

Với ví dụ, kiểm tra ba stdout là smoke test hữu ích, nhưng ứng dụng thực nên dùng assertions thay vì chỉ so sánh log bằng mắt. Chẳng hạn:
- Mặc định: target chạy một lần; TRACE enter/exit xuất hiện một lần; CHECK cho phép; kết quả ok:BOOK-1.
- Không tracing: target vẫn chạy một lần; không có sự kiện TRACE; kết quả không đổi.
- Từ chối: target không chạy; TRACE ghi nhận SecurityException; caller nhận kết quả blocked.
- Thay đổi thứ tự advice (trong một test riêng): trace sự kiện phản ánh vòng ngoài/vòng trong đúng như cấu hình.

Với retry, thêm test target fail một lần rồi thành công và một test target fail hết lượt; xác nhận số lần thực thi, exception cuối, side effect và những advice nào chạy mỗi attempt. Những thuộc tính đó quan trọng hơn việc cố định tên proxy được sinh ra.

### 10.1. Kiểm thử điểm cắt

Không chỉ hỏi “advice có chạy không?”. Cần kiểm tra **nơi không được chạy**. Dựng một phương thức match và một phương thức không match, thêm các trường hợp gọi lồng nhau, phương thức helper dùng cho logging và caller sử dụng target gốc. Nếu pointcut thay đổi khiến aspect tự bắt lại lời gọi của nó, có thể tạo reentrancy hoặc recursion.

### 10.2. Kiểm thử thread và trạng thái

Khởi tạo nhiều lời gọi đồng thời với input/identity khác nhau; xác nhận context không bị rò sang nhau. Dùng CountDownLatch hoặc điều kiện đồng bộ có kiểm soát để tái hiện race. Một test dùng Thread.sleep với timing ngẫu nhiên không đủ chứng minh thread-safety.

## 11. Quy trình phát hành và triển khai thư viện AOP dùng chung

Có thể áp dụng trình tự:

1. **Khóa contract:** xác định public API, ngữ nghĩa proceed, thông tin join point, hợp đồng exception và phiên bản Java tối thiểu.
2. **Biên dịch và kiểm thử độc lập:** build contract, engine adapter và aspect library; chạy test ở từng ranh giới.
3. **Kiểm tra phụ thuộc:** xác minh không có đường vòng từ thư viện về host-app; không kéo nhiều engine/weaver ngoài chủ đích.
4. **Xuất bản artifact có phiên bản:** artifact immutable, metadata và changelog chỉ rõ API mới, thay đổi precedence hoặc cấu hình mặc định.
5. **Lắp vào ứng dụng:** khai báo phụ thuộc và cấu hình có kiểu; tạo proxy hoặc cấu hình weaving; xác nhận pointcut áp dụng đúng.
6. **Kiểm tra artifact cuối:** chạy trên cùng dạng artifact triển khai, đặc biệt với weaving; kiểm tra startup diagnostics, performance và security.
7. **Rollout có quan sát:** giám sát lỗi phát sinh từ aspect, độ trễ, số lời gọi bị chặn, tỷ lệ retry, tác dụng phụ và khả năng rollback.

Nếu dùng Java Platform Module System (JPMS), tính visibility của package, module reads/exports/opens và khả năng truy cập reflection có thể quyết định proxy/weaver chạy được hay không. Không tự mở toàn bộ package chỉ để “hết lỗi”; dùng những quyền truy cập tối thiểu được engine yêu cầu và kiểm tra trên runtime thực tế.

### 11.1. Tránh trôi cấu hình giữa môi trường

Cùng một mã aspect nhưng khác pointcut, precedence hay cờ khởi động có thể cho hành vi khác nhau. Ghi lại **cấu hình hiệu lực** (đã loại secret) cùng fingerprint/phiên bản artifact. So sánh staging và production theo những giá trị ảnh hưởng đến join point. Bản triển khai phải có cách kiểm tra “aspect mong đợi có được kích hoạt không?”, không chỉ “ứng dụng có khởi động không?”.

### 11.2. Rollback thực chất

Hạ phiên bản thư viện có thể không khôi phục trạng thái cũ nếu cấu hình hoặc bytecode đã weave đã thay đổi. Kế hoạch rollback cần lưu phiên bản artifact, cấu hình và quy trình build/weaving tương ứng. Khi thay precedence hoặc semantics của retry/audit, coi đó là thay đổi hành vi có thể ảnh hưởng dữ liệu, không chỉ refactor package.

## 12. Những kiểu tách sai thường gặp

1. **Một thư viện chung biết mọi domain.** Aspect cố import OrderService, PaymentService, InventoryService từ các app khác; mỗi thay đổi nghiệp vụ kéo theo phiên bản thư viện mới. Hãy chuyển quyết định gắn pointcut/domain mapping về từng ứng dụng hoặc adapter domain riêng.
2. **Advice tự đọc cấu hình toàn cục trong mỗi lần gọi.** Lỗi khó tái hiện, test phụ thuộc môi trường và cấu hình giữa các request có thể không đồng nhất. Parse tập trung, inject dependency và quy định snapshot.
3. **Tập trung tất cả vào một “AspectUtils” lớn.** Khó sở hữu và dễ sinh chu kỳ phụ thuộc. Giữ contract thật nhỏ; tách policy theo trách nhiệm.
4. **Ứng dụng gọi advice thủ công.** Business service import tracer, tự gọi enter/exit cho từng phương thức; mã nghiệp vụ bị dính chặt với cross-cutting concern. Chuyển wiring đến composition root nếu yêu cầu là interception xuyên suốt.
5. **Cài đặt precedence nhờ thứ tự vô tình.** Thêm một aspect mới có thể thay lỗi/return hoặc số lần retry. Công khai thứ tự và test event sequence.
6. **Đặt cơ chế weaving trong thư viện policy nhưng không nói cho consumer.** Consumer thêm dependency mà tưởng advice tự chạy. Tài liệu hóa rõ cấu hình build/runtime, agent, classloader và khả năng pointcut.
7. **Trùng public API trong nhiều artifact.** Hai class có cùng tên nhưng tải bởi classloader/phiên bản khác làm wiring lỗi. Kiểm soát version và dependency graph.
8. **Hợp đồng quá mơ hồ về proceed.** Advice gọi nhiều lần hoặc trả sớm gây tác dụng phụ ngoài ý muốn. Ghi rõ điều kiện gọi tiếp và kiểm thử.
9. **Dùng trạng thái mutable dùng chung cho từng request.** Rò dữ liệu giữa thread hoặc bị ghi đè do song song. Chuyển state vào invocation context có vòng đời rõ.
10. **Tắt mọi kiểm soát khi config lỗi.** Đặc biệt nguy hiểm với audit/authorization. Quy định fail-fast hoặc fail-closed thích hợp cho chính sách bảo vệ.
11. **Chỉ test implementation advice, bỏ integration.** Unit test pass nhưng caller dùng target gốc nên advice không chạy. Kiểm tra đường gọi thật qua proxy/weaver và pointcut.
12. **Tách module quá sớm.** Mỗi class thành một JAR làm tăng vòng đời phát hành và dependency. Chia package trước nếu chưa có nhu cầu tái sử dụng độc lập.

## 13. Bài tập áp dụng

### Bài tập A — Thêm một aspect mà không sửa nghiệp vụ

Tạo một class TimingAdvice triển khai AroundAdvice, nhận một nguồn thời gian qua constructor (ví dụ LongSupplier), đo thời gian bao quanh next.proceed() bằng khối try/finally. Trong Main, đăng ký TimingAdvice vào danh sách. Không thay đổi OrderService.java hoặc lambda xử lý đơn hàng. Viết test dùng đồng hồ giả để tính khoảng thời gian xác định, không dựa vào thời gian chạy thật.

### Bài tập B — Đổi thứ tự để quan sát phạm vi lỗi

Đưa AccessAdvice lên trước TraceAdvice. Chạy chế độ --deny. Dự đoán xem TRACE còn in “TRACE enter” hay không, giải thích bằng thứ tự vòng ngoài/vòng trong. Ghi chuỗi sự kiện trước khi chạy để kiểm tra sự hiểu đúng ngữ nghĩa proceed.

### Bài tập C — Tách thành module thật

Đưa demo/api sang module aop-contract, demo/core sang aop-engine, demo/aspect sang aop-aspects, giữ demo/config và demo/app ở host-app. Khai báo dependency một chiều như mục 6. Chạy lại ba tình huống và so sánh stdout với bản một classpath. Nếu xuất bản JAR, kiểm tra API cùng kiểu được chia sẻ giữa engine và aspect, không chứa bản sao tách biệt.

### Bài tập D — Điểm cắt của công cụ AOP thực tế

Dùng một engine AOP bạn chọn và tạo hai phương thức: một phương thức được pointcut chọn và một phương thức nằm ngoài phạm vi. Chuyển logic TRACE sang aspect của engine và cấu hình pointcut ở tầng tích hợp. Chạy test xác nhận cả **có can thiệp** và **không can thiệp**. Mô tả chính xác cơ chế (proxy, compile-time weaving hoặc load-time weaving) và những điểm khác với ví dụ JDK proxy.

## 14. Checklist thiết kế trước khi chia sẻ thư viện

- [ ] Business target không import mã aspect hoặc engine-specific adapter.
- [ ] Hợp đồng AroundAdvice/Invocation có mô tả rõ proceed, return, exception và thread-safety.
- [ ] Aspect tập trung vào chính sách; cấu hình được parse/validate bên ngoài và inject vào.
- [ ] Pointcut, precedence và cơ chế interception có người sở hữu và cách kiểm chứng cụ thể.
- [ ] Composition root tạo các đối tượng và truyền proxy/đối tượng đã weave theo đúng đường gọi sử dụng thực.
- [ ] Package/artifact phụ thuộc một chiều, không có vòng và không nhân bản API dùng chung.
- [ ] Test gồm unit contract, cấu hình, riêng aspect, integration với cơ chế AOP thật và trường hợp không được can thiệp.
- [ ] Có quản lý phiên bản, compatibility, cấu hình hiệu lực, artifact cuối và đường rollback.
- [ ] Những cơ chế bảo vệ bắt buộc không bị vô hiệu hóa bởi cờ cấu hình thiếu kiểm soát.
- [ ] Chi phí runtime, state per-invocation, behavior khi lỗi và retry/side effect đã được đánh giá.

**Kết luận kỹ thuật:** Tách đúng không dừng ở vị trí tệp. **Hợp đồng** cho các phần nói cùng một ngôn ngữ; **aspect** hiện thực chính sách; **cấu hình** chọn cách áp dụng; **composition root/engine** nối advice với join point thật. Khi ranh giới và chiều phụ thuộc rõ, có thể thay chính sách, môi trường cấu hình hoặc cơ chế proxy/weaving với phạm vi tác động có thể đo được, đồng thời giữ nghiệp vụ độc lập và đủ bằng chứng để kiểm thử tích hợp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-integration-deployment-003">Đóng gói và kiểm tra tạo tác đã dệt</a>

<details>
<summary>Xem chi tiết</summary>

# Đóng gói và kiểm tra tạo tác đã dệt trong AOP

## 1. Mục tiêu và phạm vi

Trong **Aspect-Oriented Programming (AOP)**, *weaving* là quá trình kết hợp logic của aspect với mã tại các *join point* phù hợp. Khi quá trình này diễn ra trước khi ứng dụng chạy, kết quả thường là các tệp `.class` hoặc JAR đã thay đổi: **tạo tác đã dệt (woven artifact)**. Việc biên dịch không báo lỗi chưa đủ để kết luận tạo tác giao cho môi trường chạy đã được weave đúng, mang đủ phụ thuộc và thực hiện advice đúng chỗ.

Bài này hướng dẫn:

- Phân biệt **artifact đã dệt**, **artifact chứa aspect nhưng chưa dệt** và **cấu hình load-time weaving**.
- Xác định đầu vào/đầu ra và thứ tự đúng của quy trình **compile → weave → package → verify → test → publish**.
- Đóng gói JAR có bytecode đã được weave; kiểm tra nội dung JAR, bytecode và hành vi runtime.
- Giữ nguyên tài nguyên, metadata, hợp đồng nhị phân và phụ thuộc cần thiết khi xử lý JAR sẵn có.
- Thiết kế kiểm tra trong CI để phát hiện thiếu weaving, sai pointcut, nạp nhầm artifact, thiếu thư viện runtime hoặc tác dụng advice không mong muốn.

Ví dụ dùng **Java 17+** và compiler/weaver **AspectJ độc lập** để minh họa cơ chế AOP bằng bytecode. Các nguyên tắc đóng gói, kiểm thử và truy vết đầu ra cũng áp dụng khi dùng công cụ AOP khác có cơ chế weaving tương tự.

## 2. “Đã dệt” có nghĩa gì ở từng cách triển khai?

Một aspect định nghĩa **điều kiện khớp (pointcut)** và **hành vi bổ sung (advice)**. Không phải cứ có tệp aspect trong JAR là đã chèn được advice vào mọi lớp mong muốn.

| Cách triển khai | Tại thời điểm build có gì? | Điều kiện chạy đúng | Điểm cần kiểm tra |
|---|---|---|---|
| **Compile-time weaving (CTW)** | Compiler biên dịch nguồn Java/aspect và tạo class đã được weave | Chạy đúng class đã weave, có thư viện runtime cần thiết | Weave log, classfile, JAR cuối và smoke test |
| **Post-compile / binary weaving** | Class/JAR đã biên dịch trước được biến đổi thành **đầu ra mới** | Đóng gói **đầu ra đã biến đổi**, không nhầm bản input | Giữ tài nguyên, phát hiện trùng class, kiểm tra class khác biệt và runtime |
| **Load-time weaving (LTW)** | Artifact ứng dụng có thể vẫn chứa bytecode chưa weave; aspect và cấu hình phải sẵn có | Agent/transformer được kích hoạt trước khi loader định nghĩa lớp cần weave | Cấu hình agent, cách nạp lớp, log weaving runtime và test bằng lệnh chạy thật |
| **Interception qua proxy lúc chạy** | Artifact chứa mã nghiệp vụ và cơ chế tạo proxy/interceptor | Các lời gọi phải đi qua proxy/interception boundary | Kiểm tra luồng gọi và hành vi advice; không thể suy ra “class đã weave” chỉ từ proxy |

Đối với CTW/binary weaving, đầu ra cần được coi là **một sản phẩm build riêng**. Thư mục lớp chưa weave không được tự động ghi đè lên thư mục lớp đã weave trong bước đóng gói sau cùng.

### 2.1. Tạo tác là tập hợp nhiều thành phần

Một gói phát hành có thể gồm:

- `.class` nghiệp vụ đã được weave.
- `.class` của aspect hoặc helper được compiler sinh ra.
- File cấu hình, tài nguyên, `META-INF`, service descriptors và manifest phù hợp.
- Phụ thuộc runtime tương ứng với kỹ thuật weaving đang dùng.
- Metadata phát hành: phiên bản mã nguồn, JDK/toolchain, phiên bản weaver, phiên bản phụ thuộc và checksum.

**Chỉ kiểm tra thấy tên aspect trong JAR là chưa đủ.** Một advice có thể không khớp bất kỳ join point nào; hoặc JAR chứa aspect nhưng lớp mục tiêu vẫn là bytecode chưa biến đổi.

## 3. Quy trình build có thể kiểm chứng

Một pipeline dễ kiểm tra nên đi theo chiều một hướng:

```text
Mã nguồn nghiệp vụ + mã aspect + cấu hình pointcut
                         |
                         v
                 Compile / Weave
                         |
                  Weave info, lỗi
                         |
                         v
           Thư mục output woven sạch
                         |
                         v
           Package → application-woven.jar
                         |
          +--------------+---------------+
          |                              |
          v                              v
     Kiểm tra tĩnh                  Kiểm tra chạy
  jar/list/javap/metadata       expected advice + nghiệp vụ
          |                              |
          +--------------+---------------+
                         |
                    CI xác nhận
                         |
                         v
                Ký / xuất bản / triển khai
```

Để pipeline đáng tin cậy:

1. **Chốt đầu vào:** version mã nguồn, cấu hình pointcut, weaver, JDK và dependency. Cùng nguồn nhưng khác cấu hình weaving có thể sinh class khác nhau.
2. **Tạo output sạch, tách biệt:** ví dụ `build/woven-classes`. Tránh để class không còn trong source hoặc đầu ra lần build trước lọt vào JAR.
3. **Kiểm tra kết quả weaving:** compiler thành công, các join point quan trọng xuất hiện trong weave report, không có cảnh báo phạm vi hoặc lỗi classpath bị bỏ qua.
4. **Đóng gói sau weaving:** lấy class từ đúng output đã weave, bổ sung tài nguyên/manifest theo hợp đồng triển khai.
5. **Kiểm tra JAR cuối**, rồi chạy test sử dụng **chính JAR đó**. Test chỉ chạy class tại thư mục build không đủ để chứng minh JAR phát hành đúng.
6. **Ký và phát hành sau cùng:** nếu có ký JAR, thay đổi bytecode hoặc cấu trúc entry sau khi ký làm chữ ký không còn phù hợp.

## 4. Ví dụ end-to-end: weave, đóng gói và chạy JAR

Ví dụ này dùng cú pháp AspectJ để dệt **before advice** vào một phương thức Java. Nó không dùng bất kỳ framework ứng dụng nào.

### 4.1. Cấu trúc mã nguồn

```text
sample/
└── src/
    └── demo/
        ├── Calculator.java
        ├── Main.java
        └── AuditAspect.aj
```

Tệp `src/demo/Calculator.java`:

```java
package demo;

public final class Calculator {
    private Calculator() {
    }

    public static int sum(int a, int b) {
        return a + b;
    }
}
```

Tệp `src/demo/Main.java`:

```java
package demo;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        System.out.println("RESULT=" + Calculator.sum(3, 4));
    }
}
```

Tệp `src/demo/AuditAspect.aj`:

```java
package demo;

public aspect AuditAspect {
    before(): execution(public static int demo.Calculator.sum(int, int)) {
        System.out.println("AOP: before sum");
    }
}
```

Pointcut trên chỉ nhắm vào **thực thi** `Calculator.sum(int,int)` có đúng modifier, kiểu trả về và tham số đã khai báo. `before` chạy trước thân phương thức mỗi khi execution join point được thực hiện; mã nghiệp vụ không cần gọi aspect bằng tay.

### 4.2. Công cụ và phiên bản

Cần JDK 17+ có `java`, `javac`, `jar`, `javap`; môi trường ví dụ sử dụng **AspectJ 1.9.24** (hỗ trợ Java 17). `aspectjtools` chứa compiler/weaver; `aspectjrt` là thư viện runtime cho lớp đã weave. Giữ cùng phiên bản AspectJ cho các thành phần liên quan.

Các lệnh sau dành cho terminal kiểu POSIX (`bash` hoặc tương đương), thực thi tại thư mục `sample`. Tải dependency từ Maven Central, sau đó dùng `ajc` để biên dịch và weave:

```bash
mkdir -p tools build/woven-classes

curl -fLsS \
  https://repo.maven.apache.org/maven2/org/aspectj/aspectjtools/1.9.24/aspectjtools-1.9.24.jar \
  -o tools/aspectjtools-1.9.24.jar
curl -fLsS \
  https://repo.maven.apache.org/maven2/org/aspectj/aspectjrt/1.9.24/aspectjrt-1.9.24.jar \
  -o tools/aspectjrt-1.9.24.jar

java -cp tools/aspectjtools-1.9.24.jar \
  org.aspectj.tools.ajc.Main \
  -17 -encoding UTF-8 \
  -classpath tools/aspectjrt-1.9.24.jar \
  -showWeaveInfo \
  -d build/woven-classes \
  src/demo/Calculator.java \
  src/demo/Main.java \
  src/demo/AuditAspect.aj
```

`-17` chọn mức source/target Java 17. `-showWeaveInfo` yêu cầu compiler thông báo join point được weave. Cần **đọc và lưu** báo cáo này trong CI: mã thoát `0` không chứng minh một pointcut cụ thể thật sự khớp. Dòng weave info có định dạng phụ thuộc phiên bản nên không nên viết kiểm thử quá phụ thuộc câu chữ đầy đủ của log.

Cần loại bỏ output cũ trước mỗi lần build độc lập; ví dụ lệnh trên giả định đang làm việc trong cây build mới. CI cần thực hiện bước clean riêng theo cơ chế của hệ build trước khi chạy lệnh.

### 4.3. Đóng gói class đã weave

```bash
jar --create \
  --file build/calculator-woven.jar \
  -C build/woven-classes .
```

`-C build/woven-classes .` đưa toàn bộ class bên dưới thư mục woven vào JAR với đường dẫn tương đối như `demo/Calculator.class`. Không đóng gói lại từ `src` hay `build/plain-classes`.

**Phân biệt compiler và thư viện runtime:** artifact `calculator-woven.jar` chứa ứng dụng và aspect đã biên dịch; nó **không tự bao gồm** `aspectjrt`. Nếu muốn phát hành một JAR độc lập hoàn toàn, cần thiết kế gói phụ thuộc thích hợp (fat JAR, phân phối nhiều JAR, manifest classpath hoặc cơ chế launcher), và kiểm chứng cách xử lý resource/metadata. Không tự động coi một JAR thường là executable JAR.

### 4.4. Kiểm tra nội dung và bytecode của chính JAR

```bash
jar --list --file build/calculator-woven.jar

javap -classpath build/calculator-woven.jar \
  -c -p demo.Calculator
```

Danh sách thành phần phải có ít nhất:

```text
demo/Calculator.class
demo/Main.class
demo/AuditAspect.class
```

JAR cũng có thể chứa entry manifest và các class/metadata compiler bổ sung. Thứ tự của `jar --list` không phải bằng chứng weaving.

Trong output `javap -c -p`, quan sát bytecode **phương thức `demo.Calculator.sum`**: mã đã weave thường chứa lời gọi phụ trợ tới aspect/advice hoặc instruction bổ sung. Tên helper và cách tổ chức bytecode do phiên bản weaver quyết định nên **không yêu cầu một mẫu instruction cứng**. Nếu bytecode không đổi, điều tra pointcut, compiler input, classpath và vị trí output; tuy nhiên chỉ nhìn disassembly vẫn chưa chứng minh toàn bộ semantics đúng.

### 4.5. Chạy JAR phát hành và xác nhận stdout

```bash
java -cp "build/calculator-woven.jar:tools/aspectjrt-1.9.24.jar" \
  demo.Main
```

Đầu ra của ví dụ:

```text
AOP: before sum
RESULT=7
```

Thứ tự này chứng minh cả **before advice chạy trước thân `sum`** và **kết quả nghiệp vụ vẫn là 7**. Đây là kiểm tra chức năng, không phải benchmark. Trên Windows dùng dấu chấm phẩy `;` thay cho dấu hai chấm `:` làm ký tự phân cách classpath.

Có thể kiểm tra tự động trong terminal POSIX:

```bash
actual=$(java -cp \
  "build/calculator-woven.jar:tools/aspectjrt-1.9.24.jar" \
  demo.Main)
expected=$(printf 'AOP: before sum\nRESULT=7')
test "$actual" = "$expected"
```

Lệnh `test` trả mã thoát khác `0` khi output không khớp. Đừng chỉ kiểm tra có chuỗi `RESULT=7`: nếu thiếu advice, nghiệp vụ vẫn có thể trả 7 và bài kiểm thử quá yếu sẽ báo pass giả.

### 4.6. So sánh với artifact chưa weave để tìm lỗi nhầm đầu ra

Có thể biên dịch riêng **chỉ hai lớp Java nghiệp vụ** thành một bản baseline:

```bash
mkdir -p build/plain-classes

javac -d build/plain-classes \
  src/demo/Calculator.java src/demo/Main.java

jar --create \
  --file build/calculator-plain.jar \
  -C build/plain-classes .

java -cp build/calculator-plain.jar demo.Main
```

Kết quả baseline:

```text
RESULT=7
```

Nếu deploy artifact mang tên `calculator-woven.jar` mà output lại giống baseline, các nguyên nhân thường gặp là **đóng gói nhầm thư mục**, **pointcut không khớp**, **class được load từ một JAR khác**, hoặc **advice không có trong quá trình weaving**.

## 5. Binary weaving khi chỉ có class/JAR đã biên dịch

Khi nhận thư viện ở dạng bytecode, có thể weave bằng compiler/weaver hỗ trợ **`-inpath`** (class directory hoặc JAR đầu vào) rồi ghi ra output riêng. Ví dụ tiếp nối bản baseline ở trên:

```bash
mkdir -p build/binary-woven-classes

java -cp tools/aspectjtools-1.9.24.jar \
  org.aspectj.tools.ajc.Main \
  -17 -encoding UTF-8 \
  -classpath tools/aspectjrt-1.9.24.jar \
  -inpath build/plain-classes \
  -showWeaveInfo \
  -d build/binary-woven-classes \
  src/demo/AuditAspect.aj

jar --create --file build/calculator-binary-woven.jar \
  -C build/binary-woven-classes .

java -cp "build/calculator-binary-woven.jar:tools/aspectjrt-1.9.24.jar" \
  demo.Main
```

Output mong đợi vẫn là:

```text
AOP: before sum
RESULT=7
```

**`-inpath`** nhập class đã biên dịch làm đầu vào để weave; **`-classpath`** giúp compiler phân giải type/phụ thuộc, không tự có nghĩa tất cả class trên classpath sẽ được weave. **`-aspectpath`** dùng khi aspect đã được biên dịch ở dạng binary/library và phải được áp dụng lên các lớp đầu vào; khi đó cần cung cấp các thư viện aspect tương ứng trên runtime classpath.

Nếu input là **JAR có resource hoặc manifest riêng**, cần kiểm tra lại chính các tài nguyên đó trong output; ví dụ tối giản ở trên chỉ chứa class nên không chứng minh việc bảo toàn một JAR sản xuất phức tạp. Binary weaving cũng có thể thay đổi nội dung class làm chữ ký JAR gốc không còn hợp lệ. Không sử dụng lại chữ ký cũ như thể các byte đã được ký vẫn nguyên vẹn.

### 5.1. Những câu hỏi về input JAR cần trả lời

- Có những class nào thực sự thuộc phạm vi weaver? Liệu class phụ thuộc bên ngoài chỉ nằm trên classpath hay là input cần weave?
- Có tài nguyên `META-INF/services`, cấu hình, manifest `Main-Class`/`Class-Path`, module descriptor hoặc lớp multi-release hay không?
- Output có ghi đè input, đưa trùng phiên bản class hoặc để sót class cũ không?
- Có dùng **shading/relocation** sau weaving khiến tên package, annotation, signature hoặc pointcut khác với lúc build?
- Có aspect binary cần hiện diện lúc chạy vì mã đã weave gọi vào aspect class không?
- Bộ đóng gói có đặt class woven đúng nơi class loader thực tế tra cứu hay không?

Những câu hỏi này quan trọng hơn tên thư mục build: **chính tập bytecode mà JVM load quyết định chương trình có chạy advice hay không**.

## 6. Kiểm tra nhiều lớp: từ file đến hành vi

### 6.1. Kiểm tra cấu trúc tệp phát hành

| Lớp kiểm tra | Công cụ/kỹ thuật | Mục đích | Giới hạn |
|---|---|---|---|
| **File và đường dẫn** | `jar --list`, kiểm tra duplicate entries | Class/aspect/resource có được đưa vào đúng gói? | Có file chưa đồng nghĩa advice khớp |
| **Bytecode** | `javap -c -p`, trình đọc classfile | Có instruction hoặc helper do weaving tạo? Class version có đúng JDK đích? | Phải hiểu compiler-generated code |
| **Weave report** | `-showWeaveInfo`, diagnostics và cảnh báo build | Pointcut dự kiến đã tác động đúng kiểu/join point? | Log đúng chưa bảo đảm artifact cuối chứa đúng class |
| **Phụ thuộc** | Runtime classpath, dependency resolution, kiểm tra JAR | Lớp đã weave tìm được aspect và thư viện runtime? | Có thể bị nạp nhầm phiên bản |
| **Hành vi** | Smoke/integration tests chạy JAR cuối | Advice đúng thời điểm, đủ lượt, bảo toàn nghiệp vụ? | Một happy path không bao phủ mọi nhánh |
| **Triển khai** | Chạy bằng launcher/container/agent thật | Có cùng classpath, module path, loader và cấu hình trên môi trường đích? | Phải chọn workload đại diện |

Mỗi lớp kiểm tra trả lời một câu hỏi khác nhau. Một pipeline đạt chất lượng tối thiểu nên **kết hợp weave report, kiểm tra JAR cuối và kiểm thử hành vi**.

### 6.2. Kiểm tra bytecode và phiên bản Java

Lệnh sau in cả bytecode, metadata class và thông tin phiên bản khi cần:

```bash
javap -classpath build/calculator-woven.jar \
  -verbose demo.Calculator
```

Nếu target là Java 17, classfile tương ứng thường mang **major version 61**. Điều này xác nhận mức bytecode đầu ra, không khẳng định JAR chạy được trên mọi JVM Java 17: dependency, các API runtime và cấu hình module cũng phải phù hợp. Đừng giả định một compiler mới luôn mặc định target trùng Java 17; **chỉ định mức source/target rõ ràng**.

Khi weave thư viện Java có API nhạy cảm với binary compatibility, hãy kiểm tra tiếp:

- Method signature, visibility, generic signature và annotation mà client thực sự sử dụng.
- Serialization contract và `serialVersionUID` đối với lớp có thể serialize; việc weave có thể ảnh hưởng các chi tiết tương thích.
- Resource lookup, service loading, module descriptor, package sealing và class-loader boundary.
- Việc stack trace và line-number metadata còn đủ để debug sau weaving.

### 6.3. Kiểm thử advice phải bao phủ cả “chạy” và “không chạy”

Ngoài một trường hợp khớp, cần có trường hợp **không khớp pointcut**. Ví dụ một method khác không thuộc `Calculator.sum(int,int)` không được in dòng audit, nếu đó là hợp đồng cấu hình. Với một hệ thống có nhiều advice, kiểm tra:

1. **Số lần:** chính xác một lần mỗi join point mong muốn (hoặc số lần được định nghĩa rõ).
2. **Thứ tự:** `before`, `after returning`, `after throwing` và `around` theo semantics của cơ chế AOP đang dùng.
3. **Giá trị trả về/ngoại lệ:** advice không thay đổi vô ý result, exception, stack trace hoặc hành vi hủy.
4. **Đệ quy/self-call:** các lời gọi nội bộ thực sự là join point mục tiêu có bị advice áp dụng như dự định?
5. **Đa luồng:** advice có thread-safe khi ghi state/counter/context chung? Có tạo race condition?
6. **Biên giới lớp:** lớp được load bởi loader khác có dùng đúng woven class và phụ thuộc?
7. **Các trường hợp thiếu/không khớp runtime:** nếu loại bỏ phụ thuộc cần thiết thì build/runtime phải báo lỗi rõ ràng thay vì âm thầm bỏ advice.

Với logic quan trọng như bảo mật hoặc giao dịch, phải kiểm tra hành vi đầu-cuối; **chỉ thấy log của advice không thể thay thế kiểm thử kết quả nghiệp vụ**.

## 7. Đóng gói an toàn trong ứng dụng thực tế

### 7.1. JAR thường, executable JAR và dependency

Ví dụ đã tạo `calculator-woven.jar` là **JAR chứa class**, chạy bằng `java -cp ... demo.Main`. Lệnh `java -jar ...` chỉ hoạt động nếu JAR có manifest `Main-Class` phù hợp; hơn nữa khi dùng `-jar`, cơ chế classpath của launcher khác với truyền `-cp` ngoài lệnh. Nếu không xây gói tự chứa, cần cung cấp các JAR phụ thuộc bằng manifest `Class-Path` hoặc cơ chế chạy được kiểm chứng.

Khi tạo fat JAR, phải xử lý có chủ đích:

- Bản sao của `META-INF/services/*` và resource có tên trùng.
- Các manifest và metadata của dependency, chữ ký cryptographic không còn hợp lệ sau biến đổi/ghép.
- Thứ tự ghi khi có lớp trùng nhau; cần **fail build** nếu hai phiên bản class xung đột thay vì phụ thuộc thứ tự ngẫu nhiên.
- File license và thông tin phụ thuộc khi phân phối.
- Việc nạp `aspectjrt` hoặc runtime tương ứng trong chính môi trường sản xuất.

Hãy chạy smoke test trên **cùng kiểu đóng gói** sẽ xuất bản: nếu phát hành image/container thì kiểm tra bên trong image, không chỉ thư mục build.

### 7.2. Metadata, module và class loader

Weaving có thể sinh lời gọi tới lớp của aspect/helper. Nếu class loader đích không nhìn thấy các lớp đó, có thể phát sinh `NoClassDefFoundError` hoặc `LinkageError` ngay lần thực thi join point. Trong Java Platform Module System, module readability, exports/opens và module descriptor có thể ảnh hưởng khả năng liên kết hoặc reflection. Không dùng tùy chọn nới lỏng đóng gói một cách tự động khi chưa hiểu yêu cầu.

Đối với **multi-release JAR** (`META-INF/versions/*`), cần biết bản class nào áp dụng cho JDK triển khai. Weave sai biến thể hoặc chỉ kiểm tra base class có thể bỏ sót hành vi thực tế. Với **signed JAR**, quy trình hợp lệ thường là biến đổi → đóng gói → xác minh → ký artifact cuối, rồi xác nhận chữ ký trên chính artifact đó.

### 7.3. Không weave hai lần do pipeline lẫn lộn

Nếu vừa weave ở build vừa cấu hình transformer cùng loại lúc nạp lớp, ứng dụng có thể chịu kiểm tra thừa, weaving lại hoặc hành vi không lường trước theo khả năng reweaving của công cụ. Không giả định mọi class đã weave đều an toàn để biến đổi lần nữa. Ghi rõ **chủ sở hữu giai đoạn weaving**: build, post-compile hay runtime; test bằng cấu hình triển khai cuối.

### 7.4. Đầu vào không tin cậy và tính nguyên vẹn

Weaver xử lý bytecode do build cung cấp; binary dependency phải được quản lý với version và checksum đáng tin cậy. Khi phát hành:

- Chốt nguồn gốc và phiên bản của weaver/runtime; theo dõi thay đổi trong dependency graph.
- Ghi checksum cho artifact **sau weaving và packaging** (ví dụ SHA-256) để truy ngược đúng bản đã triển khai.
- Nếu yêu cầu build tái lập byte-for-byte, phải kiểm soát timestamp ZIP entries, thứ tự entries, manifest và input metadata; hai lần gọi `jar` thông thường không tự chứng minh đã tái lập hoàn toàn.
- Đừng sửa JAR đã ký hoặc đã phát hành mà vẫn giữ cùng danh tính/version như thể không thay đổi.

## 8. Kiểm tra CI/CD: quy tắc pass/fail có ý nghĩa

Ví dụ một pipeline tối thiểu:

```text
[1] Checkout + resolve toolchain có version cố định
[2] Clean output
[3] Compile & weave; lưu log, fail khi compiler lỗi
[4] Assert weave report có join point bắt buộc
[5] Package từ output woven, không từ input
[6] Inspect JAR: expected class/resource, không duplicate
[7] Chạy smoke test bằng JAR phát hành; so stdout/behavior
[8] Chạy unit/integration/regression với advice bật
[9] Chạy kiểm tra môi trường đóng gói cuối
[10] Tính checksum, ký (nếu cần), publish artifact đã kiểm tra
```

**Không chỉ dùng một tín hiệu:** build exit code có thể thành công dù pointcut không khớp; weave report có thể đúng nhưng bước package lấy nhầm bản chưa weave; JAR chứa đúng class nhưng runtime classpath lại chọn bản class khác.

Một ví dụ điều kiện fail chắc chắn trong smoke test đã nằm ở mục 4.5: `test "$actual" = "$expected"`. Trong ứng dụng lớn, nên thay so sánh stdout bằng assertion trên observable effect hoặc kết quả nghiệp vụ, với fixture kiểm soát được.

### 8.1. Kiểm thử phát hiện lỗi hồi quy pointcut

Khi đổi chữ ký phương thức, package hoặc modifier, pointcut tường minh có thể không còn khớp. Một số compiler có thể phát cảnh báo, nhưng **nếu không có yêu cầu về số join point bắt buộc thì pipeline vẫn có thể pass**. Quy ước hữu ích:

- Ánh xạ mỗi advice quan trọng tới một hoặc nhiều test chứng minh nó thực sự chạy.
- Lưu bản weave report để xem thay đổi join point giữa hai build, không coi chuỗi thông báo là API ổn định.
- Khi sửa pointcut, kiểm tra cả **phạm vi khớp thừa** vì aspect có thể chạm lớp không mong muốn, làm thay đổi hành vi và chi phí.
- Với aspect có quyền thay đổi dòng điều khiển (`around`), có test cho success, exception và path không gọi `proceed` nếu được thiết kế.

### 8.2. Ma trận chạy cần phù hợp sản phẩm

Chọn ma trận theo trường hợp có thật:

| Cấu hình | Cần quan sát |
|---|---|
| JDK đích thấp nhất được hỗ trợ | Class version, dependency và API dùng tại runtime |
| JDK môi trường production | Hành vi, module, class loader, JVM option |
| JAR phân phối | Woven class và runtime dependency có sẵn |
| Gói triển khai cuối (container, installer...) | Entry point, classpath, resource và smoke test thực |
| Có/nhiều Java agent khác | Transformer ordering, compatibility, không weave ngoài dự kiến |
| Đường lỗi/concurrent execution | Advice bảo toàn exception và thread safety |

Không cần nhân bản vô hạn mọi phép thử, nhưng ít nhất một bài kiểm tra phải chạy **artifact phát hành cuối cùng**, dưới cấu hình JVM gần production.

## 9. Khi artifact không hoạt động như mong đợi

| Hiện tượng | Chẩn đoán đầu tiên | Hướng khắc phục |
|---|---|---|
| Không có advice nhưng nghiệp vụ chạy | Weave report không khớp hoặc JAR lấy từ output plain | Kiểm tra pointcut và đường đóng gói; chạy đúng JAR cuối |
| Build báo weaving, runtime không có advice | JVM load bản `.class` ở classpath/module path khác | Xem loader/classpath, loại duplicate class, smoke test gói cuối |
| `NoClassDefFoundError` hoặc `LinkageError` | Thiếu aspect/helper/runtime hoặc loader không thấy chúng | Kiểm tra dependency, module và classpath thực |
| `UnsupportedClassVersionError` | Bytecode cao hơn JVM chạy | Chọn `-target` phù hợp và kiểm tra major version |
| `VerifyError` sau weaving | Bytecode transform không hợp lệ hoặc công cụ không tương thích | Kiểm tra JDK/weaver/agent, stack-map frame, biến đổi chồng chéo |
| Advice chạy hai lần | Khớp join point ngoài ý muốn hoặc dệt/chặn qua nhiều tầng | Phân tích weave report, call path và cấu hình weaving từng giai đoạn |
| JAR bị lỗi kiểm tra chữ ký | Bytecode/resource đổi sau bước ký | Weave và package trước khi ký lại tạo tác cuối |
| Test qua thư mục class pass nhưng triển khai fail | JAR thiếu resource, manifest, dependency hoặc class phù hợp | Test chính JAR/container được phát hành |
| Lỗi chỉ xuất hiện sau startup | Lớp được tải muộn, LTW thiếu transformer hoặc lazy configuration | Quan sát class loading/weave log trong luồng thực tế |

**Thứ tự debug hợp lý:** xác nhận JVM đang chạy *artifact nào* → artifact chứa *class nào* → class đã được weave *đúng join point nào* → runtime đang nhìn thấy *phụ thuộc và loader nào* → xác nhận *hành vi advice thực tế*. Nhảy thẳng sang suy đoán lỗi pointcut khi chưa xác nhận JAR được load rất dễ tốn thời gian.

## 10. Checklist nghiệm thu một tạo tác đã dệt

Trước khi xem artifact đủ điều kiện triển khai:

- [ ] Đã xác định rõ **CTW, binary weaving hay LTW**, cùng phạm vi join point cần áp dụng.
- [ ] Có đầu vào, weaver/compiler, JDK và dependency được cố định phiên bản.
- [ ] Output woven được tạo trong một nơi sạch và khác input chưa weave.
- [ ] Compiler/weaver thành công; báo cáo cho thấy **các join point bắt buộc** thực sự được dệt.
- [ ] JAR cuối chứa đúng class woven, aspect/helper, resource, manifest, module metadata cần thiết; không có class trùng gây chọn nhầm.
- [ ] Bytecode, classfile version, binary compatibility và phụ thuộc runtime đã được kiểm tra theo yêu cầu.
- [ ] Smoke test chạy từ **JAR/gói triển khai cuối**, kiểm tra cả advice và kết quả nghiệp vụ.
- [ ] Có test cho đường không khớp, ngoại lệ, thứ tự advice, nhiều luồng và call path đặc biệt nếu thuộc phạm vi chức năng.
- [ ] Cấu hình classpath/module path/agent/loader đã được kiểm tra trên JVM triển khai mục tiêu.
- [ ] Tạo tác đã xác minh được tính checksum và ký/phát hành đúng thứ tự khi cần.

## 11. Tài liệu kỹ thuật tham khảo

- [AspectJ — ajc compiler/weaver](https://eclipse.dev/aspectj/doc/latest/devguide/ajc.html): các tùy chọn `-inpath`, `-aspectpath`, `-outjar`, `-showWeaveInfo` và cơ chế biên dịch/weaving.
- [AspectJ — Java version compatibility](https://github.com/eclipse-aspectj/aspectj/blob/master/docs/release/JavaVersionCompatibility.adoc): đối chiếu phiên bản công cụ với mức ngôn ngữ/bytecode Java.
- [Maven Central — org.aspectj:aspectjrt](https://central.sonatype.com/artifact/org.aspectj/aspectjrt/1.9.24): runtime cần cho bytecode đã weave bởi AspectJ trong ví dụ.
- [JDK tools — jar](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jar.html) và [javap](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html): đóng gói, liệt kê JAR, giải mã bytecode để kiểm tra.

**Kết luận kỹ thuật:** “đã weave thành công” là thuộc tính phải chứng minh trên **đầu ra thực sự được phát hành**, bằng đối chiếu **weave report + bytecode/JAR + runtime behavior**. Tách rõ input chưa weave với output đã weave và test trên artifact cuối sẽ loại bỏ phần lớn lỗi âm thầm trong quy trình tích hợp AOP.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-integration-deployment-004">Quản lý phiên bản công cụ, định dạng mã và thư viện phụ thuộc</a>

<details>
<summary>Xem chi tiết</summary>

# Quản lý phiên bản công cụ, định dạng mã và thư viện phụ thuộc trong AOP

Aspect-Oriented Programming (AOP) cho phép đưa các mối quan tâm xuyên suốt như đo thời gian, kiểm toán hay quan sát vào nhiều điểm thực thi (join point). Khi tích hợp vào một hệ thống Java, aspect có thể được đưa vào bytecode lúc biên dịch, sau biên dịch, trong quá trình nạp lớp hoặc được thực hiện qua cơ chế chặn lời gọi tại runtime. Mỗi cách triển khai tạo ra yêu cầu tương thích riêng giữa **mã nguồn, compiler, bộ dệt aspect (weaver), định dạng class/JAR, JVM và các thư viện phụ thuộc**.

Một bản nâng cấp compiler hoặc weaver có thể khiến bản build lỗi; một bản nâng cấp JDK có thể tạo bytecode runtime cũ không đọc được; một thư viện instrumentation có thể đổi cấu trúc lớp và ảnh hưởng tới class loader. Vì vậy, quản lý phiên bản AOP không chỉ là chọn phiên bản thư viện mới nhất. Cần xác định một **tập phiên bản đã kiểm chứng**, lưu rõ cấu hình tạo artifact, kiểm tra sản phẩm sau weaving và có cách quay lui khi triển khai.

Bài này trình bày kiến thức AOP độc lập với framework ứng dụng cụ thể. Các tên như AspectJ, ASM hoặc Byte Buddy được dùng như **ví dụ về công cụ trong hệ sinh thái Java**, không đồng nhất một công cụ với toàn bộ khái niệm AOP.

## 1. Những thành phần cần quản lý phiên bản

| Thành phần | Vai trò khi sử dụng AOP | Điều cần kiểm tra |
| --- | --- | --- |
| Mã nghiệp vụ | Định nghĩa lớp, phương thức, giao diện và hành vi gốc | Phiên bản nguồn, API/ABI, kiểu join point cần áp dụng |
| Định nghĩa aspect | Pointcut, advice, kiểu dữ liệu và quy tắc ưu tiên | Cú pháp có được compiler/weaver hiện tại hiểu không; thứ tự advice |
| Trình biên dịch Java | Sinh class file từ Java source | Ngôn ngữ được hỗ trợ, chế độ target/release, metadata debug |
| Compiler/weaver AOP | Kết hợp logic aspect vào điểm thực thi | Phiên bản Java/bytecode được hỗ trợ, chế độ weaving, thay đổi hành vi |
| Runtime AOP hoặc agent | Chặn lời gọi hoặc biến đổi lớp khi tải/chạy | Khả năng tương thích JVM, class loader, module, tham số khởi động |
| Thư viện bytecode | Đọc, phân tích, viết class file | Có hiểu class-file version và cấu trúc bytecode của JDK mục tiêu không |
| Runtime Java | Xác minh và chạy bytecode, quản lý lớp | Class-file version, module access, verifier, các hạn chế instrumentation |
| Thư viện nghiệp vụ | Kiểu dữ liệu, annotations, API mà advice tham chiếu | Binary compatibility, thay đổi tên lớp/phương thức, chuyển tiếp phụ thuộc |
| Build tool và plugin | Biên dịch, weaving, test, đóng gói và xuất bản artifact | Phiên bản wrapper, plugin API, task order, khóa dependency |
| Hạ tầng đóng gói/phát hành | Tạo JAR, agent JAR, container và file cấu hình | Đúng artifact, manifest, classpath/module path, tính toàn vẹn đầu ra |

Một bản ứng dụng hoạt động khi compile chưa chắc đã hoạt động lúc nạp lớp. Quy trình quản lý phiên bản cần phân biệt ít nhất bốn mức:

1. **Tương thích nguồn:** mã Java/aspect được công cụ hiện tại phân tích và biên dịch thành công.
2. **Tương thích binary:** class sau biên dịch có thể liên kết với các class/phụ thuộc khác, không vấp lỗi như NoSuchMethodError hay NoClassDefFoundError.
3. **Tương thích runtime:** JVM và công cụ áp dụng aspect có thể nạp, xác minh và thực thi class ở đúng môi trường triển khai.
4. **Tương thích hành vi:** pointcut vẫn bắt trúng tập join point mong muốn; advice giữ đúng thứ tự, kết quả, ngoại lệ, thread safety và side effect sau nâng cấp.

Bốn mức này phải được xác minh độc lập. “Build xanh” chỉ chứng minh một phần của hợp đồng tương thích.

## 2. Chọn và ghi lại cơ chế triển khai aspect

Trước khi bàn đến phiên bản, phải xác định **cơ chế áp dụng aspect**. Tên công cụ và cấu hình không thể hoán đổi tùy ý.

| Cơ chế | Thời điểm áp dụng | Thành phần có phiên bản cần kiểm soát | Rủi ro điển hình |
| --- | --- | --- | --- |
| Compile-time weaving | Khi biên dịch source thành class | Aspect compiler, Java source/target, phụ thuộc compile | Compiler chưa hỗ trợ cú pháp hoặc class-file mới |
| Post-compile/binary weaving | Sau biên dịch, trước khi đóng gói hoặc triển khai | Weaver, class đầu vào, classpath, bytecode writer | Dệt trùng, ghi đè metadata, thay đổi class không đúng |
| Load-time weaving | Khi class loader nạp class | Weaving agent/runtime, JVM, class loader, file cấu hình | Bỏ lỡ lớp đã load, xung đột transformer, module access |
| Runtime proxy/interception | Khi lời gọi đi qua đối tượng chặn | Proxy/interceptor library, quy tắc tạo proxy, API của đối tượng | Lời gọi nội bộ không đi qua proxy, hạn chế kiểu phương thức/lớp |

**Không suy ra khả năng tương thích từ tên chế độ.** Cùng một định nghĩa advice nhưng hai cơ chế có thể nhận diện tập join point khác nhau. Ví dụ JDK dynamic proxy chặn lời gọi đi qua giao diện được proxy hiện thực; nó không tự động áp dụng tới mọi constructor, field access hoặc lời gọi trực tiếp nội bộ của đối tượng đích. Các cơ chế weaving bytecode có thể hỗ trợ tập join point khác tùy ngôn ngữ pointcut và hiện thực.

Quy ước rõ **một đơn vị chịu trách nhiệm weaving** cho mỗi artifact ở mỗi giai đoạn. Nếu vừa weave sau biên dịch vừa weave tiếp ở runtime mà không kiểm soát, một join point có thể nhận advice hai lần hoặc bị xung đột biến đổi. Không được giả định thao tác weaving luôn idempotent; phải kiểm tra khả năng reweave theo tài liệu của đúng phiên bản công cụ.

Trong hồ sơ phát hành, nên ghi: cơ chế weaving, task/build stage dệt mã, tập classes/jars đầu vào, điểm áp dụng agent, phiên bản weaver, file cấu hình, tập pointcut và thứ tự transformer nếu có nhiều agent.

## 3. Quản lý phiên bản Java và định dạng bytecode

### 3.1 Source level, target và runtime là ba thứ khác nhau

Ví dụ compiler chạy trên JDK 21 vẫn có thể được cấu hình sinh bytecode cho Java 17. Với Java, tùy chọn **javac --release 17** vừa ràng buộc cú pháp ngôn ngữ, phiên bản bytecode, vừa giới hạn API nền tảng theo Java 17. Chỉ thay đổi target mà không kiểm soát API có thể gây lỗi khi chạy trên JVM cũ hơn.

Trong class file, bốn byte đầu là magic **CAFEBABE**, tiếp theo là minor và major version. Một vài mốc thường dùng:

| Java | Class-file major |
| --- | ---: |
| 8 | 52 |
| 11 | 55 |
| 17 | 61 |
| 21 | 65 |

JVM cũ hơn mức class-file đã phát hành thường từ chối nạp với UnsupportedClassVersionError. Nhưng JVM hỗ trợ major version **không bảo đảm** weaver hoặc thư viện đọc bytecode cũng hỗ trợ; công cụ đó có parser, quy tắc xác minh và bộ ghi class riêng.

Mã sau weaving có thể khác mã đầu vào về cấu trúc phương thức, constant pool, stack map frames, exception table, generic signature và annotations. Một công cụ quá cũ có thể từ chối classfile mới hoặc ghi lại dữ liệu không hợp lệ. Vì vậy, cần kiểm tra **class-file major của cả lớp đầu vào và đầu ra** và thực sự nạp/chạy bytecode đầu ra trên JVM mục tiêu.

### 3.2 Preview features, multi-release JAR và metadata

Class file biên dịch bằng preview feature có yêu cầu runtime đặc biệt; không nên phát hành nhầm preview bytecode như một artifact tương thích Java thông thường. Nếu buộc dùng preview, cần ghi rõ phiên bản JDK và flags trong hợp đồng triển khai.

JAR đa phiên bản (multi-release JAR) có thể chứa các bản class khác nhau dưới META-INF/versions/. JVM chọn nội dung tùy phiên bản runtime. Một công cụ dệt mã phải xác định nó xử lý base classes, các phiên bản phụ, hay cả hai. Việc chỉ weave base classes có thể khiến aspect xuất hiện trên JDK này nhưng mất trên JDK khác.

Khi weave, cần bảo toàn hoặc tái tạo metadata hợp lệ: annotations, signatures, parameter names khi cần, exception attributes, stack map frames, nest/module attributes và thông tin line number để debug. Không được khẳng định metadata luôn giữ nguyên chỉ vì API public trông giống nhau.

### 3.3 Định dạng mã nguồn aspect không phải chuẩn chung của mọi công cụ

AOP mô tả các khái niệm join point, pointcut và advice. **Cú pháp cụ thể** là hợp đồng riêng của một công cụ/ngôn ngữ mở rộng. Một công cụ có thể dùng file aspect độc lập, một công cụ dùng annotations trên class Java, một công cụ dùng cấu hình bên ngoài, một công cụ khác dùng interceptor API. Các cú pháp không nhất thiết tương thích chéo.

Chẳng hạn, trong hệ sinh thái AspectJ có kiểu source aspect dưới dạng file .aj và các pointcut expression của chính ngôn ngữ đó. Việc đổi trình biên dịch sang javac thuần mà vẫn giữ source .aj không làm javac tự hiểu cú pháp aspect. Với định nghĩa aspect bằng Java annotations, compile thành công cũng chưa chứng minh aspect sẽ được dệt vào join point: còn cần đúng weaver và cấu hình ở build hoặc runtime.

Vì thế, khi nâng cấp phải ghi cả **phiên bản cú pháp/DSL, cách định nghĩa aspect, cách kích hoạt và cấu hình loader**. Không chỉ ghi “AOP enabled”.

## 4. Quy tắc quản lý phiên bản công cụ

Một hệ thống AOP nên dùng một **bộ phiên bản thử nghiệm chung (toolchain contract)**. Phần sau là mẫu thông tin cần quản lý, không phải cú pháp cấu hình bắt buộc của mọi công cụ:

```yaml
toolchain:
  language: Java
  javaRelease: 17
  targetRuntime: Java 17+
  build: Gradle wrapper pinned in repository
aop:
  mode: compile-time weaving
  sourceRepresentation: tool-specific aspect source
  aspects:
    - timing
    - auditing
  pointcutContract:
    - method executions in selected application packages
verification:
  - compile and weave from clean checkout
  - inspect emitted class-file major version
  - run integration tests against woven classes
  - verify advice invocation count and order
  - compare runtime behavior with controlled baseline
```

Các trường trên phải được hoàn thiện bằng **tọa độ artifact và phiên bản cụ thể đã kiểm thử** trong lockfile/bản kê phát hành thực tế; bản mẫu chỉ diễn đạt cấu trúc hợp đồng. Không thể suy ra một phiên bản weaver hỗ trợ Java 17 hoặc Java 21 chỉ vì tên artifact tồn tại trong kho phụ thuộc. Luôn dùng compatibility matrix, release notes và thử nghiệm của nhà cung cấp đúng cho phiên bản được chọn.

Để giữ build tái hiện được:

1. **Khóa phiên bản build tool:** commit Gradle wrapper hoặc Maven wrapper, cấu hình plugin và repository, tránh phụ thuộc môi trường máy của lập trình viên.
2. **Khai báo cụ thể compiler/weaver và runtime AOP:** ghi phiên bản trực tiếp, đừng tự động lấy bản mới nhất khi CI chạy.
3. **Kiểm soát transitive dependencies:** sử dụng dependency locking/verification hoặc lockfile tương ứng, để một thư viện bên dưới không tự đổi major version ngoài ý muốn.
4. **Lưu phiên bản JDK dùng để build và chạy:** có thể dùng Java toolchain, image digest/container hoặc mô tả môi trường CI; kiểm tra artifact trên runtime thấp nhất được hỗ trợ.
5. **Lưu checksum và nguồn tải:** dependency verification, kho artifact đã kiểm duyệt và quyền truy cập phù hợp giúp bảo vệ reproducibility và chuỗi cung ứng.
6. **Phân loại nâng cấp:** thay đổi patch/minor vẫn có thể ảnh hưởng weaver và bytecode; mọi nâng cấp công cụ biến đổi lớp cần chạy lại ma trận kiểm tra tương thích.
7. **Không ghi bản phát hành bằng nhãn nổi:** các cú pháp phụ thuộc kiểu latest, dấu cộng chọn phiên bản động hoặc khoảng version không được kiểm soát làm cùng một commit có thể cho artifact khác nhau.

**Khóa phiên bản không đồng nghĩa đóng băng vĩnh viễn.** Cần có lịch rà soát CVE, JDK end-of-support, compatibility matrix, ghi chép quyết định nâng cấp và kiểm thử theo từng bước nhỏ. Nên thử nâng cấp JDK và weaver ở hai nhánh/thí nghiệm riêng trước, sau đó mới kiểm tra tổ hợp.

## 5. Quản lý thư viện phụ thuộc và ranh giới classpath

### 5.1 Nhận diện đúng loại phụ thuộc

- **Build-only:** trình biên dịch aspect, bộ weaving, plugin kiểm tra bytecode; chỉ cần trong giai đoạn build nếu sản phẩm là class đã dệt hoàn chỉnh.
- **Compile-time:** API annotation, kiểu dữ liệu và class mà source aspect hoặc nghiệp vụ tham chiếu khi biên dịch.
- **Runtime:** runtime library cho các lời gọi do code đã weave sinh ra, interceptor library hoặc weaver chạy lúc tải lớp; không tự động coi tất cả là build-only.
- **Test-only:** thư viện kiểm thử, agent kiểm tra instrumentation, profiler hoặc công cụ giả lập tải; thông thường không đưa vào runtime production.

Việc đặt nhầm dependency có thể tạo hai kiểu sự cố đối nghịch. Nếu phụ thuộc **cần ở runtime** nhưng chỉ có khi compile, ứng dụng có thể nổ NoClassDefFoundError hoặc NoSuchMethodError sau khi triển khai. Nếu thư viện compiler/weaver không cần thiết lại đóng gói vào runtime, artifact lớn hơn, tăng khả năng xung đột và bề mặt cập nhật bảo mật.

Đối với Java agent, xem cả manifest và quy tắc JVM: một Java agent tải bằng tùy chọn **-javaagent** cần JAR có manifest khai báo Premain-Class và class chứa phương thức premain hợp lệ. Một số agent hỗ trợ attach sau khởi động bằng Agent-Class/agentmain, nhưng quyền attach và hành vi instrument phụ thuộc chính sách và phiên bản runtime. Không tự thay cách nạp agent khi chưa có kiểm thử tương đương.

### 5.2 Phân tích graph, không chỉ dependency trực tiếp

Một lỗi thường gặp: ứng dụng sử dụng weaver A, thư viện đo đạc dùng ASM/Byte Buddy phiên bản B và một agent khác mang thư viện bytecode phiên bản C. Nếu các thành phần cùng chia sẻ class loader, các bản không tương thích có thể gây linkage error. Cũng có trường hợp mỗi agent cách ly classpath riêng, nhưng vẫn xung đột do cùng biến đổi một lớp.

Đọc dependency graph ở **compile, runtime và test scopes**; lưu ý khác biệt giữa compileClasspath và runtimeClasspath, các excludes, shading/relocation và quy tắc resolution. Một kết quả dependency tree “không có hai version trùng tên” vẫn chưa chứng minh tương thích nếu hai agent tác động cùng bytecode. Ngược lại, hai thư viện có bản sao được relocation/cách ly có thể cùng tồn tại nếu được kiểm chứng.

Không tùy tiện ép toàn bộ graph lên một bản ASM/Byte Buddy mới mà chưa kiểm tra các công cụ phụ thuộc có hỗ trợ API đó. Việc ép version có thể giải quyết một xung đột tên nhưng gây lỗi bytecode parser hoặc method linkage ở chỗ khác.

### 5.3 Shading, fat JAR, module path và class loader

Khi đóng gói executable JAR hoặc shaded JAR, kiểm tra:

- **Class trùng tên:** bản nào thắng khi nhiều JAR chứa cùng FQCN? Nếu sử dụng relocation, tài nguyên và mã phản chiếu tham chiếu tên lớp có còn đúng không?
- **ServiceLoader và metadata:** các file META-INF/services, module descriptors và resources cần được merge/copy đúng; mất chúng có thể làm weaver/extension không được tìm thấy.
- **Manifest:** giữ thông tin Main-Class, Premain-Class, Multi-Release nếu cần, nhưng chỉ giữ các trường thực sự phù hợp sau đóng gói.
- **Chữ ký JAR:** sửa bytecode trong JAR đã ký làm chữ ký cũ không còn hợp lệ. Nếu cần chữ ký hợp lệ, ký lại artifact **sau** bước biến đổi, với quy trình phát hành phù hợp.
- **Class loader:** plugin/container có loader tách biệt; cùng một tên class nhưng hai loader tạo hai kiểu khác nhau. Advice có thể gặp ClassCastException nếu truyền đối tượng qua ranh giới loader sai.
- **JPMS:** module exports/opens và quyền truy cập reflection/instrumentation có thể giới hạn tác vụ. Chỉ thêm opens/exports có phạm vi cần thiết sau khi hiểu lớp và module nào cần được truy cập.
- **Native image/AOT:** runtime không có cùng hành vi class loading/instrumentation như JVM thông thường; cần thiết kế riêng theo khả năng của nền tảng và công cụ. Không mặc định một agent load-time sẽ hoạt động y hệt.

Một bản build chứa đủ JAR nhưng thiếu file cấu hình pointcut vẫn có thể **chạy mà không áp dụng advice**, nguy hiểm vì không ném lỗi rõ ràng. Bước kiểm tra phát hành nên khẳng định số join point và advice thật sự chạy.

## 6. Bảo vệ tính ổn định của định nghĩa aspect và định dạng mã

Khi nhiều nhóm cùng viết aspect, đặt quy ước nhằm giảm thay đổi âm thầm trong quá trình nâng cấp:

1. **Quy ước tên:** tên aspect, advice và pointcut nêu rõ mục đích; tránh biểu thức quá rộng áp dụng lên toàn bộ thư viện.
2. **Một nguồn cho phạm vi pointcut:** đặt biểu thức/config ở vị trí kiểm soát version, có code review và test chọn join point.
3. **Tách hành vi khỏi cơ chế:** logic ghi metric/kiểm toán nên có API nội bộ rõ ràng; syntax pointcut và weaver adapter là phần có thể thay thế có kiểm thử.
4. **Điều kiện thực thi và thứ tự advice:** ghi lại thứ tự tương tác giữa logging, đo thời gian, xác thực, retry; thay order có thể đổi ngữ nghĩa và thời lượng đo.
5. **Xử lý ngoại lệ:** around advice phải giữ nguyên kết quả trả về hoặc ngoại lệ gốc trừ khi hành vi thay đổi đã được thiết kế. Dùng finally cho cleanup/đo thời gian cần kết thúc kể cả khi lỗi.
6. **Tính reentrant và concurrent:** advice thường được gọi đồng thời. Mutable shared state, counter, cache và context phải an toàn cho luồng; không dựa vào thứ tự yêu cầu.
7. **Giới hạn tự áp dụng:** ngăn pointcut bắt vào mã hạ tầng của chính advice nếu điều đó gây recursion, double counting hoặc bão hòa logging.
8. **Format source nhất quán:** cùng charset UTF-8, quy tắc newline/indent, formatter hỗ trợ cú pháp ngôn ngữ aspect tương ứng; không dùng formatter Java thuần trên file chứa cú pháp mà nó không hiểu.
9. **Tài liệu hóa metadata:** nếu logic dựa vào tên tham số, annotations hoặc line number, test các thuộc tính đó còn sau build và weaving.

Một bản thay đổi cách đặt tên package có thể vô tình làm pointcut dạng wildcard bắt quá nhiều hoặc quá ít join point. Do đó, ngoài test chức năng, phải có **kiểm thử hợp đồng pointcut**: ở đầu vào biết trước, advice X phải chạy đúng một lần cho trường hợp A và không chạy ở trường hợp B.

## 7. Ví dụ Java 17: kiểm tra class-file format bằng chương trình chạy độc lập

Trước và sau weaving, việc kiểm tra header của class file giúp phát hiện sớm bytecode vượt phiên bản runtime mục tiêu. Ví dụ dưới đây không cần thư viện AOP; nó kiểm tra một phần của **hợp đồng artifact** và là công cụ bổ trợ, không thay thế verifier hoặc test thực thi.

Lưu toàn bộ chương trình dưới tên **ClassVersionAudit.java**:

```java
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ClassVersionAudit {
    private ClassVersionAudit() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java ClassVersionAudit <file.class>");
            System.exit(2);
        }

        Path file = Path.of(args[0]);
        try (DataInputStream in =
                     new DataInputStream(Files.newInputStream(file))) {
            if (in.readInt() != 0xCAFEBABE) {
                throw new IOException("Not a class file: " + file);
            }

            int minor = in.readUnsignedShort();
            int major = in.readUnsignedShort();
            String target = major >= 49
                    ? "Java " + (major - 44)
                    : "legacy JVM class version " + major;

            System.out.printf(
                    "%s: major=%d, minor=%d (%s)%n",
                    file, major, minor, target);
        }
    }
}
```

Dùng JDK 17 trở lên, biên dịch rõ mục tiêu Java 17:

```bash
javac --release 17 ClassVersionAudit.java
java ClassVersionAudit ClassVersionAudit.class
```

Kết quả xác định khi nguồn được biên dịch đúng bằng lệnh trên:

```text
ClassVersionAudit.class: major=61, minor=0 (Java 17)
```

Nếu cần kiểm tra file sau weaving, chạy chương trình với **đường dẫn đến .class đã dệt** (không đưa trực tiếp đường dẫn JAR vào, vì JAR không có header classfile). Muốn đọc class nằm trong JAR, trước tiên phải lấy đúng entry ra bằng công cụ ZIP/JAR hoặc viết trình đọc archive. Với multi-release JAR, kiểm tra cả các entry ứng với runtime cần hỗ trợ.

**Giới hạn của chương trình:** nó chỉ xác nhận magic, major và minor trong header; nó không chứng minh constant pool, bytecode, stack map frames, references hoặc lời gọi advice hợp lệ. Một file major 61 vẫn có thể bị VerifyError, ClassFormatError hoặc linkage error. Do đó, luôn nối bước này với việc nạp lớp, chạy test thực tế trên runtime mục tiêu và, khi cần, dùng công cụ phân tích classfile/bytecode tương thích đúng phiên bản.

## 8. Ma trận kiểm thử nâng cấp và triển khai

Một bộ test tốt xác minh cả hành vi và sản phẩm đã weave. Ví dụ quy trình cho một lần nâng phiên bản công cụ:

| Cổng kiểm tra | Thao tác | Tín hiệu PASS |
| --- | --- | --- |
| G1 — Resolve | Build từ clean checkout với wrapper và dependency lock đã commit | Cùng tập tọa độ/version/checksum; không còn version động |
| G2 — Compile | Compile source Java và định nghĩa aspect theo target release | Không lỗi syntax, classpath hoặc warning tương thích nghiêm trọng |
| G3 — Weave | Chạy bước weaving duy nhất được thiết kế; lưu danh sách class được biến đổi | Đúng lớp và join point dự kiến; không dệt trùng hoặc bỏ sót |
| G4 — Binary audit | Kiểm tra class major/minor, manifest, service resources và chữ ký | Tương thích runtime tối thiểu, artifact hợp lệ và đủ resource |
| G5 — Functional test | Gọi đường chạy bình thường, ngoại lệ và nested calls | Kết quả, ngoại lệ, side effect đúng; advice count/order khớp |
| G6 — Runtime test | Chạy sản phẩm đóng gói trong JVM/container thật | Không ClassNotFound, NoClassDefFound, VerifyError, module access hoặc agent startup error |
| G7 — Performance | So baseline và bản nâng cấp bằng tải đại diện | Độ trễ, CPU, allocation, GC và throughput nằm trong ngưỡng chấp nhận |
| G8 — Release | Phát hành artifact bất biến cùng manifest nguồn/phiên bản | Có đường quay lui tới artifact và cấu hình tương ứng |

Ma trận phiên bản tối thiểu nên bao gồm JVM **thấp nhất được hỗ trợ**, JVM triển khai chính và JVM mục tiêu của đợt nâng cấp. Nếu có nhiều class loader, nhiều chế độ weaving hoặc nhiều loại archive, bổ sung đúng các tổ hợp được vận hành; tránh kiểm thử toàn bộ tích Descartes tốn kém mà thiếu rủi ro thực tế.

Đối với agent/runtime interception, bổ sung kiểm tra thứ tự nhiều agent, thời điểm nạp class, khả năng retransform/redefine khi được sử dụng và chính sách module/attach của runtime mục tiêu. Khi đổi phiên bản thư viện bytecode, cần kiểm tra cả lớp dùng đặc trưng ngôn ngữ mới như records, sealed classes, invokedynamic hoặc nestmates nếu dự án có sử dụng.

### Kiểm tra tương đương hành vi khi thay đổi version

Viết test với danh sách trường hợp cố định:

- Lời gọi phù hợp pointcut đi qua advice đúng số lần; lời gọi không phù hợp không bị tác động.
- Advice chạy đúng thứ tự khi có nhiều aspect.
- Giá trị trả về và ngoại lệ của phương thức gốc được giữ nguyên theo hợp đồng.
- Context theo luồng/yêu cầu được tạo và dọn dẹp đúng khi chạy đồng thời.
- Không có vòng lặp instrumentation hoặc log/telemetry trùng lặp.
- Startup, class loading và chi phí advice nằm trong giới hạn đã định.

Nếu kết quả khác sau nâng cấp, phân loại nguyên nhân theo **thay đổi syntax/pointcut, bytecode generation, dependency linkage, JVM behavior hay deployment configuration** trước khi sửa.

## 9. Phát hành tái hiện được và kế hoạch quay lui

Một chiến lược triển khai an toàn cho AOP cần quản lý **cả bản đã weave lẫn cấu hình runtime**. Quy trình tham khảo:

1. Tạo artifact trong CI bằng toolchain đã khóa; chạy lại sạch từ cùng commit để phát hiện phụ thuộc version động hoặc trạng thái build sót.
2. Gắn metadata phát hành: commit, JDK release, wrapper/plugin, aspect/weaver/runtime versions, dependency lock fingerprint, loại weaving, checksum JAR và cấu hình agent nếu có.
3. Lưu artifact đã weave như sản phẩm bất biến. Không để môi trường sản xuất tự weave lại khác với bản kiểm thử nếu mục tiêu là dùng compile/post-compile weaving. Nếu dùng load-time weaving, phải quản lý version agent và config runtime tương ứng.
4. Kiểm tra gói cuối cùng sau shading, signing, containerization; đảm bảo đúng lớp được thực thi, không chỉ đúng lớp trong thư mục build tạm.
5. Triển khai theo các giai đoạn được kiểm soát, đo join point count, startup failure, latency đuôi, CPU, GC và lỗi/timeout. Một aspect không chạy mà hệ thống vẫn phục vụ yêu cầu cũng có thể là regression nghiêm trọng.
6. Khi cần rollback, quay lui **đồng bộ** artifact, weaver/agent, JVM image hoặc cấu hình liên quan. Quay lui mỗi dependency nhưng giữ class đã weave cho bản mới có thể không tương thích.

Với load-time weaving, artifact ứng dụng có thể giống nhau nhưng thay version agent hoặc cấu hình pointcut sẽ tạo bytecode trong bộ nhớ khác; do đó version của **môi trường khởi động** cũng thuộc định danh phát hành. Với compile-time weaving, artifact đã được dệt là sản phẩm kiểm thử và không nên bị công cụ khác thay đổi âm thầm về sau.

## 10. Dấu hiệu lỗi phiên bản và cách truy nguyên

| Triệu chứng | Nguyên nhân cần kiểm tra | Hướng xử lý |
| --- | --- | --- |
| UnsupportedClassVersionError | Class được compile/weave cho Java mới hơn JVM chạy | Kiểm tra major và target; build lại với release phù hợp nếu công cụ hỗ trợ |
| Unsupported bytecode/class file version trong weaver | Thư viện parse bytecode quá cũ với class input | Kiểm tra support matrix và cập nhật weaver/parser đồng bộ |
| VerifyError hoặc ClassFormatError | Stack frames, bytecode, constant pool, metadata bị tạo sai | Giữ artifact trước/sau weaving; xác định transformer tạo lỗi; dùng phiên bản hỗ trợ |
| NoSuchMethodError, NoClassDefFoundError | Dependency runtime lệch với class được compile hoặc weave | So dependency graph compile/runtime và các thư viện do advice tham chiếu |
| ClassCastException với class cùng tên | Class được nạp bởi các class loader khác nhau | Xác định loader identity, cách ly hoặc thống nhất ranh giới type |
| InaccessibleObjectException, IllegalAccessError | JPMS/module/package access không phù hợp | Kiểm tra exports/opens và quyền truy cập theo tài liệu runtime |
| Agent startup failure | Sai manifest, agent JAR, quyền attach hoặc flags | Xác nhận đúng Premain-Class, thứ tự tham số và runtime hỗ trợ |
| Build thành công nhưng advice không chạy | Pointcut không còn khớp, thiếu config hoặc weaving phase | Test join point count, xem log chẩn đoán của weaver, kiểm tra package/class loader |
| Advice chạy hai lần | Weaving lặp, proxy lồng hoặc nhiều interceptor cùng bắt | Trace đường gọi, ghi nhận cấu hình và lần áp dụng transformer |
| Chỉ một phiên bản JDK bị lỗi | Multi-release entries, metadata hoặc module behavior khác | Kiểm tra đúng class thực nạp trên từng JVM, không chỉ base JAR |
| JAR đã ký không xác thực | Bytes của class/JAR bị biến đổi sau khi ký | Sắp lại pipeline để biến đổi trước rồi ký theo chính sách phát hành |
| p99 hoặc memory tăng sau cập nhật | Weaving đổi code path, advice tạo thêm allocation/IO hoặc xung đột | So baseline, profile, kiểm tra advice count và tải đại diện |

Khi báo lỗi, đính kèm tối thiểu: stack trace đầy đủ, JVM version và flags, toolchain/weaver versions, danh sách dependency ở runtime, checksum JAR, mode weaving, thông tin class loader và class trước/sau weaving. Thiếu các thông tin đó dễ dẫn tới thay phiên bản ngẫu nhiên và tạo thêm biến trong quá trình chẩn đoán.

## 11. Ví dụ quyết định nâng cấp có kiểm soát

Giả sử ứng dụng đang chạy Java 17, có aspect đo thời gian được weave lúc build. Nhóm muốn nâng compiler/weaver lên một bản mới để xử lý cú pháp Java mới và giảm lỗi phân tích class. Các bước hợp lý:

1. Chụp **baseline** của bản đang vận hành: mã nguồn, lockfile, checksum artifact, danh sách class được weave, advice count, test và số liệu hiệu năng.
2. Chỉ nâng compiler/weaver ở nhánh thử nghiệm; tạm giữ Java release 17, runtime và dependency khác trong mức cho phép.
3. Đọc release notes và xác nhận support matrix của chính phiên bản được chọn; chạy G1–G7. Nếu compiler mới tạo metadata khác nhưng class major vẫn 61, vẫn cần test verifier và advice.
4. Nếu cần nâng Java runtime, tách thành phép thử thứ hai: dùng artifact đã kiểm tra trên JVM mới, rồi kiểm tra tổ hợp toolchain mới + JVM mới. Không suy ra tương thích từ từng thay đổi độc lập khi chúng có thể tương tác.
5. Chỉ phát hành khi pointcut contract, hành vi nghiệp vụ và ngưỡng vận hành đạt yêu cầu; nếu không, giữ artifact cũ và lưu kết quả thử nghiệm để phân tích.

Nếu chuyển từ compile-time weaving sang load-time weaving, đây là **thay đổi kiến trúc triển khai**: đường nạp lớp, thứ tự transformer, agent manifest, module access và artifact đầu ra đều có thể khác. Nó cần hồ sơ tương thích và kiểm thử riêng, không chỉ đổi một dependency rồi xem build thành công.

## 12. Checklist bảo trì cho mỗi release

- [ ] Đã ghi rõ cơ chế AOP và toàn bộ công cụ thực sự tham gia tạo/chạy aspect.
- [ ] Compiler, weaver, runtime/agent và thư viện bytecode đã được kiểm tra tương thích với JDK mục tiêu.
- [ ] Build wrapper, dependency versions, transitive graph và checksum được quản lý tái hiện được.
- [ ] Mã nguồn aspect, cú pháp pointcut, cấu hình weaving và thứ tự advice nằm trong version control.
- [ ] Không có bước weave ngoài dự kiến hoặc cùng một join point bị áp dụng trùng.
- [ ] Đã kiểm tra class-file major/minor, metadata, multi-release entries khi có, manifest, chữ ký và classpath/module path.
- [ ] Đã xác nhận API và hành vi: join point đúng phạm vi, kết quả/ngoại lệ/side effect không bị sai.
- [ ] Đã chạy artifact đóng gói thật trên JVM và môi trường tối thiểu được hỗ trợ.
- [ ] Đã so sánh startup, tải đại diện, độ trễ, CPU, bộ nhớ/GC trước khi triển khai.
- [ ] Đã lưu đủ metadata và artifact để rollback theo một tập phiên bản nhất quán.

Quản lý phiên bản AOP thành công nghĩa là **mỗi artifact có thể truy ngược được đã dùng compiler, weaver, thư viện, class-file format và runtime nào**, đồng thời có bằng chứng rằng advice chạy đúng chỗ, đúng hành vi và đúng mức chi phí. Khi những điều kiện đó được kiểm soát, việc nâng cấp và triển khai aspect trở nên có thể dự đoán, kiểm chứng và khôi phục.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-integration-deployment-005">Triển khai dệt khi nạp trong môi trường có nhiều bộ nạp</a>

<details>
<summary>Xem chi tiết</summary>

# Triển khai dệt khi nạp trong môi trường có nhiều bộ nạp lớp

## 1. Mục tiêu và bối cảnh

Trong **Aspect-Oriented Programming (AOP)**, *weaving* (dệt) là quá trình đưa logic của aspect vào các vị trí **join point** phù hợp trong chương trình. Với **load-time weaving (LTW)**, weaver biến đổi bytecode **trong quá trình class loader nạp và định nghĩa lớp cho JVM**, thay vì bắt buộc tạo sẵn mọi lớp đã dệt lúc build.

Khi hệ thống chỉ có một class loader ứng dụng, việc thiết lập LTW có thể trông đơn giản: cung cấp aspect, cấu hình pointcut và bật weaver từ đầu. Nhưng hệ thống plugin, ứng dụng cách ly theo tenant, máy chủ có nhiều module, test sandbox, chương trình có loader tự viết hoặc nền tảng nạp mã động thường có **nhiều class loader**. Khi đó, câu hỏi thực sự là:

- Loader nào **định nghĩa** lớp đích? Loader nào chỉ **ủy quyền** yêu cầu nạp cho loader khác?
- Aspect, thư viện runtime và cấu hình weaving có **hiển thị** với loader sẽ định nghĩa lớp đích hay không?
- Agent/weaver đã hoạt động **trước thời điểm lớp đích được định nghĩa** chưa?
- Hai lớp có cùng tên nhị phân nhưng do hai loader khác nhau định nghĩa có được dệt, khởi tạo và quản lý trạng thái như hai thực thể riêng không?
- Khi triển khai hoặc gỡ plugin, những tham chiếu nào khiến loader cũ không thể được thu gom rác?

Bài dùng **AspectJ** làm ví dụ thực tế cho LTW và cú pháp pointcut; các nguyên tắc về danh tính lớp, ủy quyền class loader và thời điểm định nghĩa lớp áp dụng rộng trong Java. Các công cụ AOP khác có thể có tập join point và cách tích hợp riêng. Mục tiêu là hiểu cơ chế, triển khai có giới hạn, kiểm chứng được từng loader và chẩn đoán lỗi chính xác.

## 2. Ba kiểu weaving và giới hạn của LTW

| Kiểu | Thời điểm | Đầu vào điển hình | Tính chất |
| --- | --- | --- | --- |
| Compile-time weaving (CTW) | Khi biên dịch nguồn | Java/AspectJ source | Compiler tạo ra bytecode đã dệt. |
| Post-compile/binary weaving | Sau khi có `.class`/JAR, trước khi nạp | Bytecode có sẵn | Tạo artifact mới đã dệt. |
| Load-time weaving (LTW) | Khi một loader chuẩn bị định nghĩa class | Bytecode đi qua class loading/transformer | Biến đổi tại thời điểm nạp, phụ thuộc loader và cấu hình nhìn thấy lúc đó. |

LTW không đồng nghĩa với “đợi phương thức chạy rồi mới chèn advice”. **Khi một lớp đã được JVM định nghĩa**, chỉ bật agent hoặc thêm aspect sau đó **không tự động** dệt lại lớp ấy. Java Instrumentation có những API liên quan đến retransformation/redefinition, nhưng đó là cơ chế khác, chịu giới hạn riêng; không coi AspectJ LTW thông thường là giải pháp tự động dệt lại mọi lớp đã nạp.

Trong mô hình AspectJ, dệt tại thời điểm nạp có thể được kích hoạt bởi một class loader tích hợp khả năng weaving, hoặc bởi một Java instrumentation agent như `-javaagent:/path/to/aspectjweaver.jar`. Hai cách khởi tạo khác nhau nhưng đều phải tôn trọng quan hệ class loader và thời điểm định nghĩa lớp.

## 3. Danh tính lớp Java trong hệ thống nhiều loader

Trong JVM, danh tính runtime của một class về thực chất được xác định bởi **tên nhị phân cùng defining class loader**. Tên `com.example.Plugin` giống hệt nhau không có nghĩa là cùng `Class<?>`.

```text
Bootstrap / Platform
          │
Application loader ──────── common API + shared aspect
          │
          ├── PluginLoader-A ─── com.acme.plugin.Worker
          │
          └── PluginLoader-B ─── com.acme.plugin.Worker
```

Nếu cả hai plugin loader tự định nghĩa bytecode `com.acme.plugin.Worker`, ta có hai class runtime khác nhau:

- `workerA.getName().equals(workerB.getName())` → `true`.
- `workerA == workerB` → `false`.
- Method, static field, static initializer và class-level state của Worker thuộc từng bản định nghĩa riêng.
- Ép kiểu đối tượng Worker-A sang Worker-B có thể gây `ClassCastException` hoặc `Class<?>` mismatch dù tên lớp giống nhau.
- Mỗi loader có tập lớp/resource tìm thấy và cơ chế ủy quyền riêng; do đó cùng một tên pointcut không đảm bảo mọi loader đều được weaving giống nhau.

Cần phân biệt ba khái niệm:

**Initiating loader:** loader nhận yêu cầu nạp lớp; có thể nhờ loader cha thực hiện.

**Defining loader:** loader thực sự gọi cơ chế định nghĩa class trong JVM. Đây mới là loader quyết định danh tính class và ranh giới LTW của lớp đó.

**Thread context class loader (TCCL):** loader do thread giữ trong `Thread.currentThread().getContextClassLoader()`. Một số thư viện dùng nó để tra cứu service/resource, nhưng nó **không tự biến thành defining loader** của class, cũng không tự quyết định class nào được LTW dệt. Phải quan sát trực tiếp `SomeClass.class.getClassLoader()`.

### Ủy quyền parent-first và loader tự tùy biến

Nhiều loader Java hoạt động theo kiểu **parent-first**: hỏi parent trước, sau đó mới tìm trong nội bộ. Nếu parent đã có `com.acme.plugin.Worker`, hai child loader có thể chỉ nhận đúng class parent đã định nghĩa. Khi đó chúng **không** tạo hai bản class độc lập dù đều có cùng file `Worker.class` trong URL riêng.

Một số hệ thống plugin cố ý dùng child-first cho namespace plugin. Điều đó thay đổi nơi class được định nghĩa, khả năng nhìn thấy resource, khả năng dùng aspect và rủi ro xung đột kiểu. Không nên thay delegation strategy chỉ để “ép aspect chạy” mà chưa xét compatibility, isolation và an toàn.

## 4. Ba quy tắc cốt lõi khi LTW gặp nhiều class loader

Tài liệu AspectJ quy định rõ ba nguyên tắc:

1. **Aspect phải được khai báo cho weaver trước khi các lớp đích được nạp.** Nếu class đã được định nghĩa rồi mới thêm aspect/cấu hình, các join point trên class đó có thể bị bỏ lỡ.
2. **Weaver dùng được các aspect nhìn thấy từ loader đang dệt hoặc các loader cha của nó.** Aspect nằm riêng trong loader con A không mặc định hiển thị cho loader con B.
3. **Một class loader chỉ có thể dệt những class do chính nó định nghĩa.** Loader con không thể quay lại dệt class đã được parent hoặc loader ủy quyền khác định nghĩa.

Diễn giải bằng ví dụ:

| Class đích | Defining loader | Aspect được đặt ở | Kết luận cần kiểm tra |
| --- | --- | --- | --- |
| `PluginA.Worker` | PluginLoader-A | Application loader (parent của A) | Aspect có thể nhìn thấy từ A; A cần cấu hình/agent đúng để dệt. |
| `PluginB.Worker` | PluginLoader-B | PluginLoader-A | B không thấy aspect của A theo sibling isolation thông thường. |
| `SharedApi` | Application loader | PluginLoader-A | A không được dệt lại `SharedApi` do parent đã định nghĩa. |
| `Worker` cùng tên ở A và B | Mỗi plugin loader riêng | Shared parent | Có thể dệt hai class độc lập; phải kiểm tra cả hai. |
| `Worker` đã được định nghĩa trước khi bật LTW | Bất kỳ loader | Aspect khai báo muộn | Không tự kỳ vọng weaving hồi tố. |

“Có thể nhìn thấy” là **điều kiện cần**, chưa đủ. Còn phải có cấu hình `META-INF/aop.xml` thích hợp, class nằm trong vùng `include/exclude`, các pointcut thực sự khớp join point, aspect/runtime tương thích và agent được bật đúng thời điểm.

**Lỗi thường gặp:** đặt `aspectjweaver.jar` trên classpath ứng dụng chính, thấy agent khởi động, rồi kết luận mọi class của mọi plugin đã được dệt. Điều này không đúng nếu plugin loader không nhìn thấy aspect/cấu hình, không đi qua transformer phù hợp, class đã được nạp trước hoặc pattern loại class đích.

## 5. Kiến trúc triển khai được khuyến nghị

Một sơ đồ khả thi cho nhiều plugin có chung quy tắc tracing:

```text
JVM startup
  └── -javaagent:.../aspectjweaver.jar
        └── Application loader
              ├── aspectjweaver/runtime
              ├── demo.TraceAspect
              ├── META-INF/aop.xml
              ├── HostMain
              │
              ├── PluginLoader-A
              │     └── demo.Plugin (được A định nghĩa)
              │
              └── PluginLoader-B
                    └── demo.Plugin (được B định nghĩa)
```

Luồng triển khai:

1. Khởi chạy JVM với agent đúng phiên bản.
2. Đưa aspect dùng chung và `META-INF/aop.xml` vào vị trí có thể được các loader cần dệt nhìn thấy. Với parent-first, shared parent thường là lựa chọn dễ kiểm soát.
3. Để class plugin chỉ được định nghĩa trong plugin loader tương ứng, tránh đặt trùng bản plugin trong classpath cha.
4. Trong XML, **chỉ định rõ aspect** và thu hẹp những class cần weave bằng type pattern.
5. Tải từng plugin, ghi nhận defining loader của từng class và kiểm tra số lần advice chạy.
6. Khi unload/reload plugin, loại mọi tham chiếu lâu dài đến class/instance/loader cũ, sau đó mới đánh giá khả năng GC.

Cấu trúc không nhất thiết luôn như vậy. Có hệ thống chọn aspect **riêng cho mỗi plugin** để tránh chia sẻ state, hoặc dùng class loader có hỗ trợ weaving đặc biệt. Nhưng mỗi lựa chọn phải công khai ranh giới aspect visibility, runtime dependency, configuration discovery, class identity và lifecycle.

### Khi nên đặt aspect ở parent?

Đặt aspect ở shared parent phù hợp khi muốn cùng một chính sách chạy trên nhiều plugin và chấp nhận rằng **bản class của aspect có thể được chia sẻ**. Điều này không có nghĩa advice/state tự cô lập theo tenant. Nếu aspect singleton chứa một `static Map` theo đối tượng plugin hoặc có instance field mutable, các plugin có thể cùng tác động lên state ấy. Nếu thiết kế cần isolation, phải định nghĩa state key và lifecycle rõ ràng, hoặc bố trí aspect ở loader riêng theo yêu cầu.

### Khi nào aspect nên ở child?

Khi từng plugin có chính sách riêng hoặc mã aspect không được chia sẻ với các plugin khác. Cần đảm bảo plugin loader thấy chính aspect đó và runtime mà nó dùng; aspect của plugin A không đương nhiên khả dụng với plugin B. Nếu cùng một tên aspect được định nghĩa ở hai child loader, đó là **hai class runtime khác nhau**, mang state/lifecycle riêng.

## 6. Cấu hình `META-INF/aop.xml` và phạm vi loader

Với AspectJ LTW, các tệp `META-INF/aop.xml` **nằm trên đường tìm kiếm tài nguyên của class loader** được dùng để cấu hình weaver. Một loader có thể nhìn thấy nhiều `aop.xml`, ví dụ một file trong shared parent và một file riêng trong plugin.

Cấu hình mẫu:

```xml
<aspectj>
    <aspects>
        <aspect name="demo.TraceAspect"/>
    </aspects>
    <weaver options="-showWeaveInfo">
        <include within="demo.Plugin"/>
    </weaver>
</aspectj>
```

Giải thích:

- `<aspect name="demo.TraceAspect"/>` **khai báo aspect** cho weaver; chỉ `include` không tự “tìm” aspect chưa được khai báo.
- `<include within="demo.Plugin"/>` giới hạn class đích theo type pattern. Dùng chính xác namespace triển khai; không mở rộng thành `*..*` khi không cần.
- `options="-showWeaveInfo"` bật thông tin dệt có ích khi kiểm chứng; ở production nên cân nhắc mức log để tránh chi phí và nhiễu.
- Cấu hình được đọc theo **loader/resource visibility**; cùng một file đặt trong resource path của parent có thể được nhiều child nhìn thấy theo delegation, nhưng việc dệt từng child vẫn phải được kiểm tra riêng.

Một số chi tiết cần biết:

1. Khi có **nhiều file `aop.xml` hiển thị**, các khai báo, tùy chọn và filter có thể được kết hợp theo quy tắc của weaver. Không mặc định mỗi file là một “sandbox” độc lập.
2. Các mục `<aspects><include .../></aspects>` và `<aspects><exclude .../></aspects>` lọc **tập aspect đã khai báo**; các mục `<weaver><include .../></weaver>` và `<weaver><exclude .../></weaver>` lọc **tập type sẽ dệt**. Nhầm hai vị trí này dễ khiến aspect mất hiệu lực hoặc weave sai class.
3. Nếu không có `weaver include`, phạm vi có thể rất rộng (các type không bị loại), tăng thời gian nạp và rủi ro chạm code của thư viện.
4. Nếu môi trường expose nhiều bản khác nhau của cùng aspect hoặc nhiều file XML định nghĩa trùng một *concrete-aspect* cùng tên, có thể phát sinh lỗi/xung đột. Hãy kiểm tra resource enumeration và ownership.
5. Khi include/exclude không chọn class nào, **agent vẫn có thể báo khởi động bình thường** mà advice không xuất hiện.

Các mẫu type trong `within` là **pattern**, không phải đường dẫn filesystem hay tên module. Ví dụ `com.acme.plugin..*` đại diện các kiểu trong package và package con theo cú pháp AspectJ; không bao gồm namespace ngoài vùng đó.

## 7. Ví dụ chạy: cùng một class được hai URLClassLoader định nghĩa và dệt riêng

Ví dụ chứng minh ba việc: parent thấy aspect/cấu hình, hai child loader tự định nghĩa cùng tên binary class, và advice chạy khi hai bản class được tải dưới LTW. Đây là **chương trình đầy đủ** với JDK 17+ và AspectJ compiler/weaver tương thích; không cần máy chủ ứng dụng.

### 7.1. Cấu trúc tệp

```text
project/
├── plugin-src/
│   └── demo/
│       └── Plugin.java
└── src-host/
    ├── HostMain.java
    ├── demo/
    │   └── TraceAspect.aj
    └── META-INF/
        └── aop.xml
```

**Tệp `plugin-src/demo/Plugin.java`:**

```java
package demo;

public class Plugin {
    public void work() {
        System.out.println("[business] work");
    }
}
```

**Tệp `src-host/demo/TraceAspect.aj`:**

```java
package demo;

public aspect TraceAspect {
    before(): execution(void demo.Plugin.work()) {
        System.out.println("[advice] work");
    }
}
```

**Tệp `src-host/META-INF/aop.xml`:**

```xml
<aspectj>
    <aspects>
        <aspect name="demo.TraceAspect"/>
    </aspects>
    <weaver>
        <include within="demo.Plugin"/>
    </weaver>
</aspectj>
```

**Tệp `src-host/HostMain.java`:**

```java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public class HostMain {
    public static void main(String[] args) throws Exception {
        URL pluginUrl = Path.of("plugin-classes").toUri().toURL();
        URL[] pluginUrls = {pluginUrl};
        ClassLoader parent = HostMain.class.getClassLoader();

        try (URLClassLoader first =
                     new URLClassLoader("plugin-A", pluginUrls, parent);
             URLClassLoader second =
                     new URLClassLoader("plugin-B", pluginUrls, parent)) {

            Class<?> a = Class.forName("demo.Plugin", true, first);
            Class<?> b = Class.forName("demo.Plugin", true, second);

            Object objA = a.getDeclaredConstructor().newInstance();
            Object objB = b.getDeclaredConstructor().newInstance();

            a.getMethod("work").invoke(objA);
            b.getMethod("work").invoke(objB);

            System.out.println("same-name=" + a.getName().equals(b.getName()));
            System.out.println("same-class=" + (a == b));
            System.out.println("loader-A-owns=" + (a.getClassLoader() == first));
            System.out.println("loader-B-owns=" + (b.getClassLoader() == second));
        }
    }
}
```

**Điểm cố ý trong mã:**

- Parent là application loader của `HostMain`, nơi lưu aspect và `aop.xml`.
- Parent **không** có `demo.Plugin` trên classpath; bản bytecode này chỉ nằm trong `plugin-classes` để mỗi child tự định nghĩa.
- Mỗi `URLClassLoader` có tên và URL giống nguồn bytecode nhưng là đối tượng loader khác nhau.
- `getClassLoader()` dùng để kiểm tra defining loader; không dùng TCCL để suy ra class identity.
- Reflection gọi đúng một lần `work()` trên mỗi bản class, đủ để đếm advice mà không cần framework.

### 7.2. Biên dịch và chạy

Giả sử biến `ASPECTJ_HOME` trỏ đến bản AspectJ tương thích với JDK 17 và lệnh `ajc` đã có trong `PATH`. Từ thư mục `project/`, ví dụ trên shell Linux/macOS:

```bash
mkdir -p host-classes/META-INF plugin-classes
javac -d plugin-classes plugin-src/demo/Plugin.java
ajc -17 \
  -classpath "plugin-classes:$ASPECTJ_HOME/lib/aspectjrt.jar" \
  -d host-classes src-host/demo/TraceAspect.aj
javac -d host-classes src-host/HostMain.java
cp src-host/META-INF/aop.xml host-classes/META-INF/aop.xml

java \
  -javaagent:"$ASPECTJ_HOME/lib/aspectjweaver.jar" \
  -cp "host-classes:$ASPECTJ_HOME/lib/aspectjweaver.jar" \
  HostMain
```

Nếu cài AspectJ theo cấu trúc khác, điều chỉnh đường dẫn JAR theo môi trường. Tệp `aspectjweaver.jar` được cung cấp cho JVM agent; compiler `ajc` cần runtime JAR trên classpath biên dịch. Trên Windows, dấu phân cách classpath là `;`; việc copy và biến môi trường cũng phải thay theo PowerShell/cmd.

**Stdout nghiệp vụ dự kiến** (khi weaving thành công và bỏ qua các diagnostic riêng nếu môi trường bật verbose):

```text
[advice] work
[business] work
[advice] work
[business] work
same-name=true
same-class=false
loader-A-owns=true
loader-B-owns=true
```

Giải thích:

- Hai dòng `[advice] work` chứng minh advice chạy trên hai lần execution do **hai class runtime độc lập**.
- `same-name=true` nhưng `same-class=false` phản ánh Java class identity.
- Hai kiểm tra `loader-...-owns=true` xác nhận mỗi child là defining loader, do đó agent có thể áp dụng weaving tại ranh giới loader tương ứng.
- Nếu bốn dòng nhận dạng cuối vẫn đúng nhưng không có dòng `[advice]`, việc định nghĩa class đã thành công mà **LTW hoặc pointcut/aspect visibility** chưa được xác minh; xem mục chẩn đoán.

**Điểm kiểm thử quan trọng:** không cho `plugin-classes` vào `-cp` của lệnh chạy `HostMain`. Nếu vô tình đặt nó vào parent classpath, parent-first delegation có thể khiến cả A và B nhận *cùng class do parent định nghĩa*; bài kiểm thử nhiều loader sẽ không còn đúng.

### 7.3. Thử nghiệm phản chứng

1. **Tắt `-javaagent`:** cả hai child vẫn định nghĩa được `demo.Plugin` và in business, nhưng advice không chạy. Đây là dấu hiệu để tách lỗi cấu hình weaving khỏi lỗi classloading.
2. **Đưa `plugin-classes` vào classpath parent:** tùy thứ tự nạp, cả hai yêu cầu có thể được parent đáp ứng; `same-class` có thể thành `true` và kiểm tra ownership thành `false`. Không còn phép thử “mỗi child định nghĩa class”.
3. **Đặt aspect chỉ bên loader A:** B không đương nhiên nhìn thấy aspect A. Cần xét lại visibility, XML và đường đóng gói; không kết luận chỉ cần thêm một file XML ở B.
4. **Nạp class trước khi cấu hình aspect:** không kỳ vọng LTW chuẩn dệt hồi tố; tạo loader mới/nạp class mới với cấu hình đúng sẽ là phép thử rõ ràng hơn.

Ví dụ này minh họa kiến trúc, không có nghĩa mọi hệ thống plugin có khả năng đặt aspect ở parent. Thực tế phải tuân theo delegation policy, module descriptors, security constraints, classloader custom implementation và thứ tự transformer.

## 8. Thiết kế classpath và đóng gói nhiều plugin

### 8.1. Tách API chung khỏi implementation

Kiến trúc thường dùng:

```text
Shared parent loader
  ├── com.acme.api.PluginApi
  ├── com.acme.aspect.AuditAspect
  └── META-INF/aop.xml
        │
        ├── plugin-A loader → com.acme.pluginA.Worker
        └── plugin-B loader → com.acme.pluginB.Worker
```

API contract nằm ở shared parent giúp `PluginApi` chỉ có một danh tính class dùng chung. Các implementation nằm ở child để hỗ trợ thay thế, nạp lại và cách ly. Aspect có thể đặt tại parent nếu cần quan sát cả hai, với include pattern như `com.acme.pluginA..*` và `com.acme.pluginB..*` hoặc một namespace chung phù hợp.

Để plugin implement interface được chia sẻ, đừng đóng gói lại bản class `PluginApi` vào child theo cách child-first tự định nghĩa bản trùng. Nếu không, cùng một interface trên source có thể trở thành hai runtime types không assign được.

### 8.2. Tránh tạo hai bản runtime AspectJ không tương thích

Khi plugin đem theo `aspectjrt.jar` hoặc `aspectjweaver.jar` riêng, các loader có thể nhìn thấy bản khác nhau của các kiểu `org.aspectj.*`. Điều này tạo rủi ro version mismatch, linkage error và việc aspect tham chiếu runtime API khác với cái host đang dùng. Hãy xác định **ai sở hữu phiên bản weaver/runtime**, lớp nào thấy thư viện đó và có cần cô lập bản runtime riêng không.

Một giải pháp “thêm mọi JAR vào mọi classpath” có thể tạm chữa `ClassNotFoundException` nhưng phá isolation. Cần quản lý dependency visibility có chủ đích, đặc biệt khi plugin thay đổi độc lập.

### 8.3. Chú ý classpath so với JPMS module path

Java 9+ có thêm Java Platform Module System (JPMS). *Class loader visibility* và *module readability/exports/opens* là các ranh giới khác nhau. Có thể loader nhìn thấy class nhưng liên kết/truy cập phản chiếu bị chặn bởi module boundary. Với named module, cần kiểm tra module graph và yêu cầu export/open cụ thể của phần mềm, không mặc định LTW luôn vượt qua module encapsulation.

Trong một môi trường dùng module layer riêng cho từng plugin, cần xác định **layer**, **class loader**, **module readability** và **thời điểm instrumentation**. Đừng coi module layer là class loader hoặc cho rằng thêm `--add-opens` tùy tiện sẽ sửa mọi lỗi weaving.

## 9. Thứ tự khởi động và vòng đời class loader

Trình tự triển khai nên được thiết kế trước:

```text
(1) JVM khởi động + đăng ký Java agent
      ↓
(2) Shared aspect/runtime/config hiển thị
      ↓
(3) Khởi tạo child loader hoặc plugin container
      ↓
(4) Child loader đọc/ủy quyền cấu hình, chuẩn bị class
      ↓
(5) Bytecode class đích qua transformer/weaver
      ↓
(6) JVM defineClass → Class<?> trở thành class đã định nghĩa
      ↓
(7) Gọi phương thức, kiểm tra advice
      ↓
(8) Đóng loader, loại tham chiếu khi unload/redeploy
```

**Trạng thái “agent đã cài” không cho biết mọi bước sau đều đã xảy ra.** Một class có thể bị nạp do static initializer, reflection, dependency injection, service discovery, bytecode inspection hoặc cache của framework trước khi plugin init hoàn tất.

Với reload plugin:

- Loader mới định nghĩa class mới, dù tên và bytecode không đổi.
- Class và instance từ loader cũ không thể tự biến thành class/instance từ loader mới.
- Aspect đặt ở parent có thể giữ các tham chiếu đến class/instance/loader cũ trong static map, listener, thread, timer hoặc executor, làm loader cũ không unload được.
- `URLClassLoader.close()` giải phóng tài nguyên mở của loader (ví dụ JAR handle), **không đảm bảo class unloading** nếu vẫn còn reachable objects/threads/references.
- Không giữ `Class<?>` hoặc `Method` của child trong cache global vô hạn; sử dụng cache có lifecycle rõ ràng, weak references đúng chỗ và teardown các callback.

Việc class được dệt không cho phép đoán mức memory leak; phải quan sát heap dump, classloader instances còn sống, ThreadLocal và reference chains.

## 10. Quan sát, logging và xác minh theo từng loader

Để không đoán mò, kết hợp chứng cứ **từ JVM** và **từ weaver**.

### 10.1. Nhận diện loader của class và tài nguyên

Java 17 có thể dùng:

```java
Class<?> type = object.getClass();
ClassLoader defining = type.getClassLoader();
System.out.println(type.getName());
System.out.println(defining);
System.out.println(type.getModule());
```

Đoạn này chỉ có thể chạy trong ngữ cảnh đã có biến `object`. Nếu `getClassLoader() == null`, class thuộc bootstrap loader; không phải lỗi tự thân. Giá trị `toString()` của loader (đôi khi kèm hash) phù hợp log chẩn đoán, không nên dùng để khẳng định danh tính duy nhất thay cho so sánh tham chiếu hoặc mapping theo lifecycle.

Khi nghi ngờ `aop.xml`, kiểm tra việc enumeration tài nguyên với loader cụ thể:

```java
import java.net.URL;
import java.util.Enumeration;

public class ResourceProbe {
    public static void report(ClassLoader loader) throws Exception {
        Enumeration<URL> urls = loader.getResources("META-INF/aop.xml");
        while (urls.hasMoreElements()) {
            System.out.println(urls.nextElement());
        }
    }
}
```

Đây là lớp utility, chưa phải chương trình độc lập có `main()`. Kết quả là các resource URL **nhìn thấy qua loader**; nó không chứng minh tất cả đều đã được weaver sử dụng theo ý muốn. Cần đối chiếu log của agent. Với loader tùy biến, thứ tự enumeration và parent delegation có thể khác nhau.

### 10.2. Bật chẩn đoán AspectJ có mục tiêu

Ví dụ cấu hình tạm thời trong `META-INF/aop.xml`:

```xml
<aspectj>
    <aspects>
        <aspect name="demo.TraceAspect"/>
    </aspects>
    <weaver options="-verbose -debug -showWeaveInfo">
        <include within="demo.Plugin"/>
    </weaver>
</aspectj>
```

Các tùy chọn phục vụ mục đích khác nhau:

- `-verbose`: thông tin cấu hình/bootstrap của weaver.
- `-debug`: thêm thông tin xử lý class, kể cả weaving/exclusion/ignore theo phiên bản.
- `-showWeaveInfo`: thông tin khi class/advice được dệt.
- JVM `-Daj.weaving.verbose=true` có thể hữu ích để đưa một số thông báo bootstrap ra sớm, trước khi phân tích cấu hình.

Lưu ý hai tầng log: **weaver reports** cho thấy class nào được transform và aspect/advice nào được đăng ký; **application events** xác nhận đúng behavior trong thực thi. Một log báo weaver initialized chưa chứng minh mọi Plugin được weave; một dòng advice chưa chứng minh cả loader A/B đã được phủ.

### 10.3. Bảng kiểm chứng khi có hai child loader

| Kiểm tra | Loader A | Loader B | Cách xác minh |
| --- | --- | --- | --- |
| Class binary name | `demo.Plugin` | `demo.Plugin` | `Class.getName()` |
| Defining loader | A | B | So tham chiếu `getClassLoader()` với loader kỳ vọng |
| Có `aop.xml` nhìn thấy | Có/không | Có/không | `getResources("META-INF/aop.xml")` |
| Aspect nhìn thấy | Có/không | Có/không | Class/resource visibility và weaver registration |
| Class match include/exclude | Có/không | Có/không | Cấu hình + diagnostics |
| Advice được weave và thực thi | Có/không | Có/không | Weave info + test chạy phương thức |
| Class bị load sớm | Có/không | Có/không | Log classloading/agent startup |
| Loader unload được sau đóng | Đo riêng | Đo riêng | Heap analysis / lifecycle test |

Đừng chỉ báo “LTW đang chạy” ở mức JVM. Trong môi trường nhiều loader, kết luận có giá trị hơn là: *aspect X, configuration Y được nhìn thấy bởi loader A/B; class Z do loader đó định nghĩa, đã dệt; advice P chạy N lần trong workload cố định*.

## 11. Ma trận lỗi thường gặp và cách gỡ

| Hiện tượng | Nguyên nhân có thể | Hướng kiểm chứng |
| --- | --- | --- |
| Có log agent khởi động nhưng không có advice | `aop.xml` không hiển thị, aspect chưa khai báo, include không match, class đã load trước | Xác minh resource, aspect list, weave info và timing. |
| Advice chạy ở plugin A nhưng không ở B | Aspect/config nằm riêng trong A; B có class loader sibling | So loader graph, `getResources` và registered aspects ở B. |
| Hai child nhưng chỉ một bản class được thấy | Parent đã định nghĩa target do parent-first delegation | Kiểm tra `a == b` và defining loader; bỏ target khỏi parent path khi muốn child ownership. |
| `ClassNotFoundException` / `NoClassDefFoundError` | Thiếu aspect/runtime/API trong loader visibility hoặc phụ thuộc của class | Kiểm tra binary name, dependency graph, classpath và loader links. |
| `ClassCastException` giữa hai bản class cùng tên | Different defining loaders | Chia sẻ interface/API ở parent hoặc dùng boundary serialization/adaptation. |
| `LinkageError` / verifier error | Bytecode transform conflict, trùng type khác loader, runtime version mismatch | Chốt exact exception, dump class, kiểm tra version/transformer order. |
| Aspect không áp dụng cho class core | Class được định nghĩa bởi loader parent/bootstrap, hoặc bị loại bởi weaver rules | Xác định defining loader, kiểm tra supported scope/exclusions. |
| Weaving chỉ hiệu lực sau restart | Class đã được định nghĩa từ trước khi cấu hình mới có hiệu lực | Đảm bảo agent/config sớm; thử loader mới và restart có kiểm soát. |
| Redeploy plugin làm tăng heap | Static cache/aspect parent giữ class/instance/loader cũ | Heap dump, threads, listeners, ThreadLocal, teardown. |
| Chậm khởi động/plugin loading | Too-broad `include`, debug logging, transform nhiều class | Giới hạn type pattern, đo class count và thời gian load. |

### Quy trình gỡ lỗi theo thứ tự

1. **Xác định đối tượng:** ghi class binary name, class loader thực tế, module và đường JAR của class đích.
2. **Xem graph loader:** ai là parent, có child-first không, class đã được parent định nghĩa chưa?
3. **Kiểm tra tài nguyên:** `META-INF/aop.xml` nào thực sự được loader thấy; có nhiều bản trùng không?
4. **Kiểm tra aspect:** aspect class có trong loader hoặc parent, runtime API tương thích và được khai báo trong XML chưa?
5. **Kiểm tra thời điểm:** Java agent có được kích hoạt từ JVM startup, trước khi class đích được định nghĩa?
6. **Kiểm tra chọn class:** include/exclude có thực sự chứa target class, có namespace viết đúng không?
7. **Kiểm tra join point:** advice pointcut loại `execution/call` có phù hợp thân phương thức hoặc call site được dệt không?
8. **Bật weave info tạm thời:** đối chiếu từng loader/class, thay vì dựa vào stdout nghiệp vụ duy nhất.
9. **Chạy case tối giản:** một phương thức ở A, một ở B; xác định số advice mong đợi rồi so sánh.
10. **Đánh giá lifecycle:** khi plugin reload, class cũ có còn bị giữ bởi aspect/static cache/thread không?

Nếu đã có nhiều agent chuyển đổi bytecode, ghi rõ **thứ tự transformer**, phạm vi class, phiên bản và biến đổi nào được thực hiện. Bytecode từ transformer trước có thể là đầu vào cho transformer sau; thứ tự có thể quan trọng và gây ra lỗi không xuất hiện ở môi trường chỉ có một agent.

## 12. Những giới hạn kỹ thuật phải tôn trọng

### 12.1. Không phải mọi class đều có thể weave bằng một cấu hình

Theo tài liệu LTW AspectJ, một số namespace hạ tầng như `org.aspectj.*` và các class nền tảng `java.*`, `javax.*` thuộc nhóm không được expose cho LTW của chính AspectJ, bất kể include. Cũng cần phân biệt: có thể weave một **call join point** nằm trong class ứng dụng được dệt khi nó gọi API nền tảng, nhưng điều đó không đồng nghĩa bytecode của class nền tảng đã bị dệt.

### 12.2. Loader tự viết có thể có hành vi đặc biệt

Các loader custom có thể kiểm soát `findClass`, parent delegation, URL/resource visibility, package sealing, bytecode generation và cách nạp JAR. Không mặc định `URLClassLoader` trong ví dụ bao phủ mọi plugin framework. Nếu loader tạo lớp động hoặc không cung cấp resource thông thường, cần kiểm tra agent/weaver thực sự nhận bytecode nào.

### 12.3. Class initialization khác với class definition

`Class.forName(name, true, loader)` yêu cầu nạp/khởi tạo class theo ngữ nghĩa API, nhưng quan trọng hơn cho LTW là **lớp được định nghĩa lúc nào**. Class có thể được define trước khi static initializer chạy hoặc trước lần gọi phương thức đầu tiên. Do đó “chưa gọi method lần nào” không chứng minh class còn cơ hội được LTW dệt.

### 12.4. Nạp lại và dệt lại là hai vấn đề khác nhau

Class loader mới có thể định nghĩa lại class cùng tên như một **runtime identity mới**, khiến LTW có cơ hội xử lý bytecode khi nạp. Đó không phải là weave lại **Class<?> đã tồn tại**. AspectJ cũng có khái niệm reweavable bytecode trong một số pipeline; không coi đó là quyền sửa mọi class đã định nghĩa trong JVM theo thời gian thực.

### 12.5. Resource lookup và security

Tệp XML hiển thị cho một loader không có nghĩa aspect class, package access và quyền thao tác đều hợp lệ. Khi có giới hạn module/security hoặc classpath không đồng nhất, có thể xuất hiện failure khi resolve aspect hoặc verify class. Không “chữa” bằng cách đưa tất cả lớp của mọi plugin lên shared parent, vì cách đó có thể làm mất isolation của plugin.

## 13. Hiệu năng, cô lập và vận hành lâu dài

LTW ảnh hưởng không chỉ latency của advice mà cả **thời gian tải class** và tài nguyên:

- Mỗi class được dệt có chi phí phân tích/biến đổi bytecode lúc nạp; khởi động nhiều plugin có thể đẩy chi phí về giai đoạn loading.
- Weaver cần tìm/đọc cấu hình và resolve aspect cho các loader liên quan; nhiều aop.xml visible hoặc classpath rộng làm chẩn đoán phức tạp hơn.
- Include/exclude rộng có thể đưa class thư viện không cần thiết vào phạm vi xử lý; nên dùng pattern giới hạn theo package/type đã xác nhận.
- Advice chạy trên đường nóng có thể phát sinh overhead CPU, allocation và logging; vấn đề này độc lập với thời điểm weaving.
- Khi có nhiều loader định nghĩa cùng các tên class, số bản class và metadata runtime tăng; thêm cache/static reference có thể khiến loader không unload.
- Một aspect chung chứa mutable state phải đảm bảo thread safety và isolation; per-loader class identity **không tự cô lập** một aspect singleton nằm ở parent.
- Khi cần zero-downtime redeploy, chạy kiểm thử tạo/đóng loader nhiều lần và quan sát class count, live loader count, heap, threads và callbacks.

### Tiêu chí chấp nhận khi đưa vào môi trường thật

- [ ] Agent/weaver và version chạy đúng từ startup.
- [ ] Danh tính class loader cho từng module/plugin được ghi nhận.
- [ ] Aspect class/runtime/aop.xml nhìn thấy ở đúng loader scope.
- [ ] Include/exclude chỉ chọn những class mong muốn; không vô tình chạm core dependency.
- [ ] Test cho mỗi child loader chứng minh advice khớp và không khớp theo dự kiến.
- [ ] Thời điểm class definition được xác minh; không phụ thuộc cấu hình bổ sung quá muộn.
- [ ] Đã thử nested calls, exception, concurrency và classloader reload khi có liên quan.
- [ ] Metrics về startup time, class loading, heap và advice latency ở mức phù hợp.
- [ ] Lifecycle của plugin không bị aspect/global cache/thread giữ tài nguyên cũ.
- [ ] Có kế hoạch tắt/bật LTW và tái hiện vấn đề bằng cấu hình cố định để rollback.

## 14. Bài tập và câu hỏi tự kiểm tra

**Bài 1 — class identity.** Loader A và B có cùng URL chứa `demo.Plugin.class`, nhưng parent không chứa class này. Cả hai dùng parent-first và gọi `Class.forName(..., true, loader)` riêng. Hai `Class<?>` chắc chắn là cùng đối tượng không?

**Đáp án:** không. Nếu mỗi child tự định nghĩa lớp, hai đối tượng `Class<?>` khác nhau dù cùng tên và bytecode. Phải kiểm tra defining loader thực tế.

**Bài 2 — parent class.** Plugin A muốn advice tác động lên `com.acme.SharedApi` đã được Application loader định nghĩa. Chỉ thêm `SharedApi` vào include của aop.xml nằm trong plugin A có đủ không?

**Đáp án:** không. Loader A không thể weave class mà parent đã định nghĩa. Cần thiết kế việc weave đúng loader và đúng thời điểm class được định nghĩa; không tạo bản trùng API chỉ để lách hạn chế này.

**Bài 3 — sibling visibility.** Aspect được định nghĩa chỉ trong PluginLoader-A, còn target nằm ở PluginLoader-B. Theo mô hình loader sibling độc lập, B có tự nhìn thấy aspect không?

**Đáp án:** không. B thông thường chỉ thấy class do nó định nghĩa hoặc do parent/delegate phù hợp, chứ không tự nhìn thấy sibling A. Muốn chia sẻ aspect, tổ chức classloader graph/đóng gói có chủ đích.

**Bài 4 — cấu hình thấy nhưng advice không chạy.** `getResources("META-INF/aop.xml")` trả về URL đúng, nhưng gọi `work()` không thấy advice. Hãy kể ít nhất ba nguyên nhân.

**Đáp án:** aspect chưa được khai báo/resolve; class bị exclude hoặc không match pointcut; class đã load trước khi weaving; hoặc agent không transform class được loader định nghĩa. Chứng cứ cần thêm là log weave info, class identity và thứ tự load.

**Bài 5 — reload.** Đã gọi `close()` trên URLClassLoader nhưng heap vẫn giữ nhiều class của plugin cũ. Có thể kết luận `close()` thất bại không?

**Đáp án:** không. `close()` không bắt buộc unload class ngay; phải kiểm tra các tham chiếu còn tồn tại, chẳng hạn static cache ở parent aspect, live threads, ThreadLocal, listener hoặc objects/plugin instances.

**Bài 6 — aop.xml.** Trong XML có `<weaver><include within="com.acme..*"/></weaver>` nhưng không có `<aspects><aspect name="..."/></aspects>`. Có thể coi `include` tự khám phá và kích hoạt mọi aspect trên classpath không?

**Đáp án:** không. `include` ở phần `weaver` giới hạn type để dệt; aspect cần được khai báo theo cơ chế cấu hình tương ứng.

## 15. Kết luận và tài liệu tham khảo

Triển khai LTW trong nhiều class loader không chỉ là việc thêm `-javaagent`. Kết quả phụ thuộc vào **loader thực sự định nghĩa class**, **aspect/config có hiển thị đúng ranh giới hay không**, và **weaver đã sẵn sàng trước class definition chưa**. Kết hợp ba điều kiện đó với pointcut đủ chính xác, kiểm chứng độc lập trên từng loader và quản lý lifecycle sẽ giúp triển khai AOP linh hoạt mà vẫn duy trì isolation.

Tài liệu đọc thêm:

- [AspectJ Development Guide — Load-Time Weaving](https://eclipse.dev/aspectj/doc/latest/devguide/ltw.html): khái niệm, ba quy tắc class loader, cấu hình, agent và giới hạn LTW.
- [AspectJ — Load-Time Weaving Requirements](https://eclipse.dev/aspectj/doc/released/devguide/ltw-rules.html): aspect visibility và ranh giới class loader.
- [AspectJ — Dumping classes during LTW](https://eclipse.dev/aspectj/doc/released/pdguide/ltwdump.html): lấy bytecode trước/sau weaving để phân tích lỗi khó.
- [JDK 17 API — ClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): delegation, defining loader và class-loading contract.
- [JDK 17 API — URLClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html): API loader dùng trong ví dụ và lifecycle `close()`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-integration-deployment-006">Loại trừ mã sinh, thư viện ngoài và công cụ quan sát khi cần</a>

<details>
<summary>Xem chi tiết</summary>

# Loại trừ mã sinh, thư viện ngoài và công cụ quan sát khi cần trong Aspect (AOP)

## Mục tiêu

Một aspect được thiết kế để thực thi một mối quan tâm xuyên suốt chương trình, ví dụ đo thời gian, ghi nhật ký, kiểm tra điều kiện, theo dõi lỗi hoặc quản lý tài nguyên. Khi điểm cắt (**pointcut**) khớp quá rộng, advice có thể chạy trên cả mã được sinh tự động, mã thư viện bên ngoài và chính cơ chế quan sát mà advice sử dụng. Kết quả có thể là log trùng, overhead lớn, lời gọi đệ quy, deadlock, mất khả năng chẩn đoán hoặc thay đổi hành vi nghiệp vụ.

Bài này tập trung vào **cách quyết định những nơi cần loại trừ, thực hiện loại trừ ở đúng tầng và xác minh không vô tình bỏ sót chức năng quan trọng** trong lập trình hướng khía cạnh (Aspect-Oriented Programming, AOP) nói chung. Bạn sẽ:

- Phân biệt **miền được áp dụng advice** với **miền được weaving/instrumentation**.
- Nhận diện trường hợp mã sinh, thư viện ngoài hoặc logger/tracer/metric collector cần được bỏ qua.
- Thiết kế **allowlist trước, exclusions có lý do rõ ràng sau**, xét đến class, method, annotation, classloader và loại join point.
- Hiểu các khác biệt giữa loại trừ lúc weaving và chỉ bỏ qua xử lý trong runtime advice.
- Kiểm thử được phạm vi, tính đúng đắn, bảo mật và hiệu năng sau khi thay đổi pointcut.

Không tồn tại một cú pháp pointcut hay quy tắc loại trừ duy nhất áp dụng cho mọi công cụ AOP. Các ví dụ về biểu thức pointcut trong bài là **mã giả**; hãy chuyển chúng sang cú pháp của cơ chế đang sử dụng.

## 1. Vì sao loại trừ là một phần của thiết kế aspect?

Các khái niệm nền:

| Khái niệm | Vai trò |
| --- | --- |
| **Join point** | Vị trí mà hệ thống AOP có thể gắn hành vi, tùy khả năng công nghệ: thực thi/gọi phương thức, truy cập trường, constructor... |
| **Pointcut** | Điều kiện chọn những join point phù hợp |
| **Advice** | Mã được thực thi trước, sau hoặc bao quanh join point được chọn |
| **Weaving** | Quá trình đưa hành vi aspect vào chương trình, có thể ở bước biên dịch, xử lý bytecode, tải class hoặc khi chạy |
| **Instrumentation** | Việc bổ sung hành vi quan sát/biến đổi cho mã; không phải mọi instrumentation đều là cùng một công nghệ AOP |
| **Exclusion** | Chính sách bỏ qua class, method, artifact, join point hoặc lần gọi theo lý do xác định |

Giả sử một aspect ghi thời gian đã được thiết kế cho thao tác nghiệp vụ `calculatePrice()`:

```text
User request
    |
    +--> calculatePrice()            ← cần đo
              |
              +--> vendorMoneyLib()   ← có thể không cần đo từng hàm
              |
              +--> metricRecorder()   ← không nên tạo metric lồng vô hạn
                        |
                        +--> encoder()
                        +--> queue()
                        +--> exporter()
```

Nếu pointcut được viết theo kiểu “mọi phương thức” thì những thao tác `encoder()`, `queue()` hoặc `exporter()` có thể cũng bị chặn. Khi chính advice gọi `metricRecorder()`, nó kích hoạt lại advice, tạo một chu trình:

```text
Advice bắt đầu
  → ghi metric
    → metricRecorder() khớp pointcut
      → advice bắt đầu
        → ghi metric
          → ...
```

Kết cục có thể là `StackOverflowError`, stack tăng, CPU bão hòa hoặc số lượng event/log tăng ngoài kiểm soát. Trường hợp khác là thư viện ngoài liên tục sinh các method nhỏ, gây tăng số lần chạy advice mà không tạo giá trị quan sát tương xứng.

**Mục tiêu loại trừ:** chỉ áp dụng advice cho những join point thực sự thuộc hợp đồng quan sát hay hành vi mà aspect chịu trách nhiệm. Loại trừ phải có nguyên nhân cụ thể, không dựa vào suy đoán rằng “mọi mã sinh” hoặc “mọi thư viện” đều không quan trọng.

## 2. Phân biệt hai tầng phạm vi: weaving và thực thi advice

Đây là khác biệt quyết định cả hiệu năng lẫn tính đúng đắn.

### 2.1. Loại trừ trước hoặc trong weaving

Tùy công nghệ, có thể cấu hình để **không biến đổi** một nhóm class/module, hoặc định nghĩa pointcut sao cho không chọn join point bên trong chúng. Tác động thường là:

- Không gắn advice vào các join point bị loại trừ.
- Giảm số vị trí phải áp dụng hoặc lưu metadata cho advice; mức giảm startup, memory và runtime tùy cơ chế.
- Tránh chặn những API lõi như logger/exporter trước cả khi chúng hoạt động.

Với load-time weaving hoặc bytecode instrumentation, cần phân biệt **lọc class được đưa vào transformer** với **lọc join point** trên class đã được xử lý. Một class có thể chứa cả method nên áp dụng và method phải loại trừ. Chính sách loại trừ toàn class sẽ ảnh hưởng cả hai.

### 2.2. Điều kiện bỏ qua trong advice

Nếu join point đã được weave hoặc đã đi qua proxy/interceptor, advice có thể kiểm tra điều kiện:

```java
// Mã giả: phụ thuộc API AOP thực tế.
if (isInternalTelemetry(joinPoint) || isExcludedInvocation(joinPoint)) {
    return proceed();
}
return observeAndProceed(joinPoint);
```

Cách này có ích khi điều kiện chỉ được biết lúc chạy: loại request, mức log, cờ sampling, caller context hoặc trạng thái chống đệ quy. Nhưng lời gọi **vẫn có thể phải đi qua interception**, tạo chi phí dispatch, kiểm tra điều kiện và lấy metadata.

Không thể khẳng định guard runtime hoàn toàn thay thế được cấu hình weaving. Ngược lại, chỉ loại trừ lúc weaving cũng chưa giải quyết hết đệ quy nếu aspect gọi API quan sát có thể kích hoạt **một join point khác vẫn được bao phủ**.

### 2.3. Quy tắc lựa chọn

- **Loại trừ tĩnh ở mức class/package/module** khi vùng đó rõ ràng nằm ngoài phạm vi, nhiều lời gọi và cần tránh chi phí interception.
- **Loại trừ ở mức method/annotation** khi chỉ một số phương thức trong class cần bỏ qua.
- **Guard runtime** khi quyết định phụ thuộc dữ liệu của từng invocation hoặc cần lớp bảo vệ chống đệ quy.
- **Kết hợp các mức** khi vừa cần tiết kiệm overhead, vừa có đường gọi đặc biệt dễ sinh vòng lặp.

Cú pháp, tên matcher và thời điểm tính pointcut khác nhau giữa các công cụ. Trước khi chọn cách thực hiện, hãy xác định cơ chế thực sự đang dùng: compile-time weaving, post-compile transformation, load-time weaving hay runtime interception/proxy.

## 3. Các nhóm đối tượng thường cần xem xét loại trừ

### 3.1. Mã được sinh tự động (generated code)

Mã sinh có thể đến từ annotation processor, compiler, công cụ tạo proxy, chuyển đổi schema, bộ mã hóa dữ liệu, RPC stub, mapper hoặc công cụ tạo binding. Mã này có thể chứa:

- Method chuyển tiếp ngắn gọi rất nhiều lần.
- Bridge/synthetic method do compiler sinh ra.
- Các lớp proxy/adapter với tên thay đổi theo bản build hoặc lúc chạy.
- Getter/setter, mapper hoặc serializer nội bộ làm tăng mạnh số event.
- Code quản lý vòng đời/bất đồng bộ không phù hợp với advice vốn giả định lời gọi nghiệp vụ bình thường.

**Nhưng không loại trừ tất cả mã sinh theo mặc định.** Ví dụ RPC stub sinh tự động có thể là ranh giới quan trọng để đo độ trễ từ ứng dụng đến hệ thống ngoài; proxy sinh ra có thể là điểm interception duy nhất của một luồng gọi. Loại trừ sai tầng có thể xóa bỏ tín hiệu giám sát mà bạn thực sự cần.

Nên phân loại theo **vai trò**, không chỉ cách mã được tạo:

| Nhóm mã sinh | Câu hỏi trước khi loại trừ |
| --- | --- |
| DTO, mapper, serialization helper | Có cần từng method nội bộ xuất hiện trong log/trace không? |
| RPC/client stub | Có cần đo ranh giới gọi từ ứng dụng sang hệ thống khác không? |
| Proxy/adapter động | Pointcut muốn chặn proxy, target hay cả hai? Có làm ghi đúp không? |
| Bridge/synthetic method | Matcher có vô tình đếm cùng một thao tác hai lần không? |
| Generated business implementation | Đây có phải logic nghiệp vụ mà aspect phải bao phủ không? |

Các dấu hiệu hỗ trợ nhận diện gồm package output riêng, metadata build, annotation có retention phù hợp và các thuộc tính reflection như `Method.isSynthetic()` hay `Method.isBridge()`. Chúng không phải dấu hiệu toàn năng:

- Một class sinh tự động chưa chắc có annotation `@Generated` ở runtime.
- Có annotation mang tên “Generated” nhưng retention không cho phép đọc qua reflection.
- `isSynthetic()` hay `isBridge()` không bao quát mọi mã do generator tạo; một method sinh ra vẫn có thể bình thường theo bytecode.
- Tên class chứa ký tự `$` không đồng nghĩa “toàn bộ class là mã sinh cần bỏ qua”; inner class do người viết cũng có ký tự này.

Để lọc tin cậy, ưu tiên thông tin đã biết từ **build artifact, source set, generator ownership hoặc classloader/module** hơn các heuristic về tên.

### 3.2. Thư viện bên ngoài (third-party libraries)

Thư viện JSON, HTTP, database driver, cryptography, logging, RPC hoặc collection có thể chạy nhiều phương thức nội bộ. Chặn mọi lời gọi nội bộ có thể gây:

- Phình số lượng span/log/metric và làm mất tín hiệu nghiệp vụ.
- Chi phí runtime đáng kể trên những method rất ngắn.
- Đụng vào code có yêu cầu reentrancy, locking, classloading hoặc initialization nhạy cảm.
- Thay đổi hành vi do advice ném ngoại lệ, trì hoãn lời gọi hoặc dùng sai contract của thư viện.
- Ghi lại dữ liệu bí mật hoặc payload nhạy cảm mà thư viện xử lý.

Thư viện thường nên được quan sát ở **ranh giới tích hợp** mà ứng dụng gọi vào, thay vì mọi implementation detail bên trong. Tuy nhiên, nếu mục tiêu là profiling, kiểm tra bảo mật hoặc quan sát latency của một API thư viện cụ thể, có thể chọn **một phần API công khai** của nó với chính sách rõ ràng.

Vì một package có thể chứa mã nhiều chủ sở hữu, không nên coi “namespace giống ứng dụng” là bằng chứng tuyệt đối cho ownership. Hãy xét phối hợp classpath artifact, package, module, classloader và metadata triển khai.

### 3.3. Công cụ quan sát và chẩn đoán (observability tooling)

Đây là nhóm đặc biệt cần chú ý vì có nguy cơ **instrumentation tự quan sát chính nó**:

- Logger, formatter, appender, encoder và bộ đẩy log.
- Tracer, span processor, context propagator, trace exporter.
- Metric registry, meter/recorder, exporter và collector.
- Profiler, agent/helper instrumentation, event dispatcher.
- Health monitor hoặc watchdog gọi lại chính code được quan sát.

Nếu không loại trừ phù hợp, một advice ghi log có thể lại chặn logger; một advice tạo span có thể lại tạo span cho hành vi xuất span; một bộ đo timing lại đo chính thao tác ghi timing. Kết quả có thể là vòng lặp, nhân bội event hoặc nhiễu số đo.

Không có nghĩa là hệ thống quan sát không bao giờ được tự quan sát. Bạn vẫn có thể cần metric riêng để giám sát queue depth, export failure, dropped records hoặc thời gian flush. **Hãy dùng đường đo riêng được giới hạn và không gọi vòng vào cùng advice**, hoặc chủ động chọn ranh giới quan sát cụ thể.

## 4. Thiết kế allowlist và exclusion list

Một chính sách dễ kiểm soát thường bắt đầu bằng **đối tượng được phép**, rồi xác định ngoại lệ. Mục tiêu là tránh một pointcut toàn cục quá rộng phải bổ sung vô số quy tắc chữa cháy.

Mã giả:

```text
shouldAdvise(joinPoint):
    if !belongsToApprovedApplicationScope(joinPoint):
        return false

    if isInternalLoggingOrTelemetry(joinPoint):
        return false

    if isExcludedGeneratedHelper(joinPoint):
        return false

    if isExcludedExternalImplementation(joinPoint):
        return false

    if hasEffectiveNoAdviceMarker(joinPoint):
        return false

    return matchesBusinessJoinPointPolicy(joinPoint)
```

Đây là **thứ tự quyết định đề xuất** cho một chính sách quan sát: ở mỗi bước, có thể loại trừ mà không gọi logic nghiệp vụ. Cần tính cả các quy tắc ưu tiên cao hơn nếu cơ chế AOP của bạn có nhiều aspect; không được tự động áp dụng cùng một chính sách loại trừ cho các aspect giữ vai trò bảo mật hoặc đảm bảo tính nhất quán.

### 4.1. Phạm vi theo package hoặc module

Ví dụ tổ chức khái niệm:

```text
app.domain.*           ← nghiệp vụ, được xem xét chặn
app.usecase.*          ← nghiệp vụ, được xem xét chặn

app.generated.mapping.* ← helper sinh tự động, loại trừ
app.telemetry.*         ← logging/tracing/metrics, loại trừ
vendor.internal.*       ← implementation thư viện, loại trừ

app.integration.rpc.*   ← chỉ chọn method ở ranh giới cần đo
```

Các chuỗi `app.*` hay `vendor.*` là **đường dẫn minh họa**, không phải các tên package thực tế của mọi ứng dụng. Nếu dùng glob/regex, hãy kiểm tra ý nghĩa `*`, `..`, tên class lồng và boundary của dấu chấm trong bộ matcher đang sử dụng. Một biểu thức quá rộng có thể vô tình khớp package khác có tên tương tự.

Không nên dựa vào quy tắc “mọi thứ không thuộc một package là thư viện ngoài”. Ứng dụng nhiều module có thể chứa code sở hữu ở nhiều namespace khác nhau.

### 4.2. Loại trừ theo annotation

Một marker như `@SkipObservation` hoặc `@NoAspect` có thể là hợp đồng rõ ràng cho những vị trí không muốn quan sát. Khi dùng marker cần xác định:

- Annotation có `RUNTIME` retention không nếu matcher kiểm tra qua reflection?
- Được áp dụng trên class, method hay cả hai?
- Có kế thừa hoặc áp dụng cho method override/interface/bridge hay không? Điều này phụ thuộc cơ chế tìm metadata.
- Một method mang marker tại implementation nhưng advice nhìn thấy chữ ký interface thì resolver có tìm đúng implementation không?
- Có được phép dùng marker để bỏ qua một kiểm tra bảo mật bắt buộc không? Cần chính sách quyền hạn riêng.

Không biến annotation thành nút tắt tất cả cross-cutting concerns. Có thể định nghĩa marker chuyên trách như `@SkipDiagnosticLogging` để bỏ log chi tiết, trong khi enforcement cần thiết vẫn hoạt động.

### 4.3. Loại trừ theo loại join point và method metadata

Tùy công nghệ AOP, pointcut có thể nhắm một hoặc nhiều loại join point. Có thể loại trừ constructor, accessor, static initializer hoặc bridge/synthetic method nếu chúng không thuộc mục tiêu, nhưng cần kiểm tra cơ chế có thực sự cung cấp những join point ấy không. Runtime proxy thông thường không thể coi như bytecode weaving toàn diện.

Kiểm tra cả overload, override, interface default method, method ẩn/có giới hạn truy cập và lời gọi nội bộ trên cùng đối tượng. Một cơ chế proxy chỉ chặn những lời gọi đi qua proxy; một cơ chế weave bytecode có thể có phạm vi khác. Nếu bảo vệ bắt buộc không được kích hoạt ở một lời gọi, đó có thể là **coverage gap do interception mechanism**, không phải do quy tắc exclusion.

## 5. Thứ tự ưu tiên của nhiều aspect và tránh advice chồng chéo

Ngay cả khi mỗi pointcut được viết đúng, nhiều aspect có thể cùng khớp một join point:

```text
Request
  → Advice tracing
    → Advice logging
      → Advice timing
        → Business method
```

Nếu logging ghi toàn bộ span, còn tracing cũng ghi span và timing tự phát metric tại cùng biên, dữ liệu có thể trùng và overhead nhân lên. Khi nhiều advice bao quanh nhau, **thứ tự bọc** quyết định cách đo duration, điều kiện nhìn thấy exception và vùng dữ liệu context có hiệu lực.

Để tránh chồng chéo:

1. Gán rõ nhiệm vụ cho từng aspect: ai tạo trace span, ai ghi business event, ai chỉ đo metric tổng hợp.
2. Chọn **một ranh giới được quan sát chuẩn** cho mỗi thao tác nếu cần sự kiện “một request”.
3. Xác định thứ tự advice theo khả năng công nghệ đang dùng; test bằng vết thực thi.
4. Loại trừ helper nội bộ của advice khác, nhưng không làm mất context cần truyền qua các tầng.
5. Đặt guard cho lời gọi lồng nhau nếu một event chỉ được ghi tại tầng ngoài cùng; phải khôi phục state sau khi thoát.

Cần tránh giải pháp “tắt mọi thứ trong công cụ quan sát” mà không đánh giá nhu cầu đo lỗi exporter, queue và backpressure. Các metric này thường có thể được phát qua cơ chế không tự đi vào cùng pointcut.

## 6. Ví dụ Java 17+: mô phỏng chính sách chỉ quan sát nghiệp vụ được chọn

Ví dụ sau dùng **JDK dynamic proxy** để minh họa logic chọn áp dụng advice. Proxy chặn lời gọi qua interface; nó không chứng minh việc loại trừ tại compile-time/load-time weaving. Dù vậy, cách đặt hợp đồng allowlist, exclusion, method marker và đếm số lần advice chạy có thể dùng để thảo luận thiết kế phạm vi trong các hình thức AOP khác.

Chính sách demo:

- Chỉ các class được đánh dấu là **application component** mới đủ điều kiện.
- Component được đánh dấu **generated helper**, **external implementation** hoặc **telemetry infrastructure** bị loại khỏi logging aspect, kể cả khi nằm trong nhóm application component.
- Một method được đánh dấu **skip observation** bị bỏ qua ngay cả khi class đủ điều kiện.
- Phần việc thật được thực thi y nguyên dù có hoặc không chạy advice.

Tạo tệp `AspectExclusionDemo.java`:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

public class AspectExclusionDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface ApplicationComponent {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface GeneratedHelper {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface ExternalImplementation {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface TelemetryInfrastructure {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface SkipObservation {}

    interface UseCase {
        String execute(String value);
        String health();
    }

    @ApplicationComponent
    static final class BusinessUseCase implements UseCase {
        @Override
        public String execute(String value) {
            return "business:" + value;
        }

        @Override
        @SkipObservation
        public String health() {
            return "up";
        }
    }

    @ApplicationComponent
    @GeneratedHelper
    static final class GeneratedMapper implements UseCase {
        @Override
        public String execute(String value) {
            return "generated:" + value;
        }

        @Override
        public String health() {
            return "up";
        }
    }

    @ApplicationComponent
    @ExternalImplementation
    static final class ExternalAdapter implements UseCase {
        @Override
        public String execute(String value) {
            return "external:" + value;
        }

        @Override
        public String health() {
            return "up";
        }
    }

    @ApplicationComponent
    @TelemetryInfrastructure
    static final class TelemetryWorker implements UseCase {
        @Override
        public String execute(String value) {
            return "telemetry:" + value;
        }

        @Override
        public String health() {
            return "up";
        }
    }

    private static boolean shouldIntercept(Class<?> type) {
        return type.isAnnotationPresent(ApplicationComponent.class)
                && !type.isAnnotationPresent(GeneratedHelper.class)
                && !type.isAnnotationPresent(ExternalImplementation.class)
                && !type.isAnnotationPresent(TelemetryInfrastructure.class);
    }

    private static final AtomicInteger ADVICE_CALLS = new AtomicInteger();

    static UseCase wrapIfSelected(UseCase target) {
        if (!shouldIntercept(target.getClass())) {
            return target;
        }

        InvocationHandler handler = (proxy, interfaceMethod, args) -> {
            Method implementationMethod = target.getClass().getMethod(
                    interfaceMethod.getName(), interfaceMethod.getParameterTypes());

            if (!implementationMethod.isAnnotationPresent(SkipObservation.class)) {
                ADVICE_CALLS.incrementAndGet();
                System.out.println("advice:" + implementationMethod.getName());
            }

            try {
                return implementationMethod.invoke(target, args);
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }
        };

        return (UseCase) Proxy.newProxyInstance(
                UseCase.class.getClassLoader(),
                new Class<?>[] {UseCase.class},
                handler
        );
    }

    public static void main(String[] args) {
        UseCase business = wrapIfSelected(new BusinessUseCase());
        UseCase generated = wrapIfSelected(new GeneratedMapper());
        UseCase external = wrapIfSelected(new ExternalAdapter());
        UseCase telemetry = wrapIfSelected(new TelemetryWorker());

        System.out.println("result=" + business.execute("x"));
        System.out.println("health=" + business.health());
        System.out.println("result=" + generated.execute("x"));
        System.out.println("result=" + external.execute("x"));
        System.out.println("result=" + telemetry.execute("x"));
        System.out.println("adviceCalls=" + ADVICE_CALLS.get());
    }
}
```

Biên dịch/chạy với JDK 17 hoặc mới hơn:

```bash
javac AspectExclusionDemo.java
java AspectExclusionDemo
```

Đầu ra kỳ vọng:

```text
advice:execute
result=business:x
health=up
result=generated:x
result=external:x
result=telemetry:x
adviceCalls=1
```

**Giải thích:** `BusinessUseCase.execute()` đủ điều kiện nên kích hoạt advice một lần. `BusinessUseCase.health()` có marker `@SkipObservation` nên không được log. Ba component còn lại được trả về nguyên bản bởi `wrapIfSelected`, không đi qua proxy của ví dụ và không tạo advice event. Tất cả vẫn chạy logic nghiệp vụ.

**Giới hạn cần hiểu:** Marker `@ApplicationComponent` và các marker loại trừ là metadata **giả lập ownership trong ví dụ**, không có công cụ nào tự động gán chúng cho mã sinh hoặc thư viện ngoài. Trong thực tế, không thể yêu cầu mọi thư viện phụ thuộc đều gắn annotation của ứng dụng; thông tin build/classpath hoặc rule cấu hình sẽ phù hợp hơn. Với runtime proxy, method trên interface có thể khác method triển khai; ví dụ chủ động tìm method thực bằng reflection để kiểm tra marker. Nếu method có overload/bridge, hoặc code dùng nhiều interface và inheritance phức tạp, resolver thực tế phải được kiểm tra thêm.

Hơn nữa, ví dụ chỉ dùng điều kiện để **quyết định tạo proxy**. Trong một hệ thống weaving, muốn tiết kiệm chi phí biến đổi hoặc loại trừ đúng join point, điều kiện tương tự phải được chuyển sang điểm lọc do hệ thống đó hỗ trợ. Một lời gọi nội bộ không đi qua proxy có thể không được advice dù class đủ điều kiện; đây là thuộc tính của cơ chế proxy, không phải bằng chứng của quy tắc exclusion.

## 7. Exclusion tại compile-time, load-time và runtime: khác nhau ra sao?

| Thời điểm/cơ chế | Đơn vị lọc thường gặp | Điều cần xác minh |
| --- | --- | --- |
| Compile-time hoặc post-compile weaving | Class, member, join point theo cấu hình công nghệ | Artifact nào được weave, advice nào được áp dụng, debug symbols/metadata |
| Load-time weaving/Java agent | Class được nạp, classloader, tên class, bytecode/join point | Transformer có bỏ qua helper/tooling/agent class và vùng JVM nhạy cảm không |
| Runtime proxy/interceptor | Đối tượng được bao bọc, interface/method, predicate runtime | Lời gọi có đi qua proxy không; trường hợp self-invocation, overload và visibility |
| Guard bên trong advice | Invocation, context, loại thao tác, sampling | Có còn overhead interception không, guard có an toàn trước đệ quy không |

Không phải hệ thống nào cũng hỗ trợ mọi mức lọc, và “loại trừ package” không chắc loại trừ cả subpackage nếu cú pháp matcher không định nghĩa như vậy. Ngoài ra, class được tải bởi nhiều classloader có thể cùng tên nhưng là những `Class` khác nhau. Quy tắc dựa trên tên thuần có thể chưa đủ để xác định provenance hoặc identity.

### 7.1. Đặc biệt với load-time instrumentation

Một transformer chạy khi JVM định nghĩa class. Nếu nó biến đổi chính class của logger, agent helper hay bytecode library mà transformer đang sử dụng, có thể tạo vòng nạp class, tác động đến class initialization hoặc lỗi verifier. Vì thế cần **bộ lọc đủ sớm** trước khi phân tích/chỉnh sửa bytecode và bộ test khởi động với các cấu hình classloader cần hỗ trợ.

Lưu ý:

- Đánh giá những vùng JVM/module hoặc classloader không nên instrument bằng công cụ đang dùng.
- Loại trừ các helper classes dùng để thực hiện chính transformation.
- Kiểm tra tương thích bytecode/version, thứ tự transformer và việc nhiều công cụ instrument cùng một class.
- Đảm bảo rule vẫn đúng khi class sinh động có tên thay đổi và khi thư viện được nâng cấp.
- Không biến một bộ lọc dựa trên tiền tố chuỗi thiếu ranh giới thành điều kiện đánh trúng class có tên tương tự.

Ví dụ khái niệm: nếu muốn loại bỏ đúng package `com.example.telemetry` và các lớp con của nó, parser hoặc matcher cần được kiểm tra về ranh giới tên. So khớp `startsWith("com.example.telemetry")` một cách tùy tiện còn có thể bỏ nhầm `com.example.telemetryBusiness`.

### 7.2. Đặc biệt với runtime proxy

Proxy có thể khiến bạn quan sát **đối tượng bọc** thay vì implementation thật. Khi viết predicate, cần làm rõ muốn kiểm tra type của proxy, interface hay class đích. Nếu kiểm tra “tên proxy có chứa một hậu tố sinh tự động”, bạn có thể loại luôn lời gọi nghiệp vụ rất quan trọng. Nếu chặn cả proxy lẫn target bằng hai tầng khác nhau, một thao tác có thể tạo hai bản ghi.

Thứ tự lựa chọn pointcut và thứ tự wrapper phải có test bao gồm lời gọi trực tiếp, lời gọi qua interface, lời gọi từ method cùng class và lời gọi nested. Không suy luận khả năng chặn lời gọi chỉ từ việc thấy annotation tồn tại.

## 8. Guard chống đệ quy cho chính công cụ quan sát

Loại trừ tĩnh nên là phòng tuyến chính. Trong một số thiết kế, vẫn cần guard theo execution context vì có những đường gọi bất ngờ đi ngược vào advice. Một cách phổ biến là guard dùng `ThreadLocal` để đánh dấu luồng hiện tại đang thực thi phần instrumentation.

Mã giả:

```java
// Mã minh họa, không phải chương trình Java độc lập.
private static final ThreadLocal<Boolean> IN_TELEMETRY =
        ThreadLocal.withInitial(() -> false);

Object around() throws Throwable {
    if (IN_TELEMETRY.get()) {
        return proceed();
    }

    IN_TELEMETRY.set(true);
    try {
        return observeAndProceed();
    } finally {
        IN_TELEMETRY.remove();
    }
}
```

**Đây chỉ là bản đơn giản**, đúng cho trường hợp không có context cũ cần phục hồi. Nếu có advice lồng nhau hoặc scope trước đó có giá trị khác, cần lưu và phục hồi **giá trị trước** thay vì luôn `remove()`. Nếu dùng thread pool mà quên dọn trong `finally`, cờ có thể vô tình làm tắt quan sát ở các request sau.

Một phiên bản phác thảo có lưu trạng thái cũ:

```java
// Mã minh họa, không phải chương trình Java độc lập.
Boolean old = REENTRY_GUARD.get();
if (Boolean.TRUE.equals(old)) {
    return proceed();
}
REENTRY_GUARD.set(Boolean.TRUE);
try {
    return observeAndProceed();
} finally {
    if (old == null) REENTRY_GUARD.remove();
    else REENTRY_GUARD.set(old);
}
```

Điểm cần thận trọng:

1. **Một guard bao trùm toàn bộ `proceed()`** có thể làm mất việc quan sát những thao tác nghiệp vụ lồng nhau, dù chúng là mục tiêu hợp lệ. Muốn chỉ chặn đệ quy từ logger/exporter, nên áp guard quanh chính đoạn phát telemetry hoặc dùng trạng thái/pointcut chính xác hơn.
2. `ThreadLocal` thông thường không tự lan truyền context sang thread khác; lời gọi bất đồng bộ có thể kích hoạt lại instrumentation ở thread khác. Nếu truyền context qua task, phải thiết kế vòng đời và cleanup rõ ràng.
3. Guard chỉ là chính sách **bỏ qua logic advice tại runtime**; nếu pointcut vẫn khớp rộng, chi phí interception vẫn có thể tồn tại.
4. Nếu instrumentation ném ngoại lệ, cleanup phải chạy trên cả đường lỗi. Hành vi fail-open hay fail-closed của advice phụ thuộc hợp đồng, không được quyết định tùy tiện.

Một hệ thống quan sát nghiêm túc thường có **đường xuất dữ liệu độc lập** không đi lại vào cùng pointcut, kết hợp exclusions cho infrastructure và guard hẹp để phòng đường gọi bất ngờ.

## 9. Không loại trừ nhầm phần bắt buộc

Exclusion là công cụ giảm rủi ro/chi phí, nhưng cũng có thể tạo **lỗ hổng phạm vi**. Một số aspect có vai trò ảnh hưởng đến tính đúng đắn hoặc an toàn, chẳng hạn kiểm tra quyền truy cập, giới hạn tài nguyên, thực thi ràng buộc hoặc đảm bảo trạng thái nhất quán.

Giả sử hệ thống có:

```text
UseCase nghiệp vụ
  → Adapter sinh tự động
    → API nhạy cảm
```

Nếu một kiểm tra bảo mật bắt buộc chỉ được gắn ở adapter mà lại loại trừ toàn bộ mã sinh, kiểm tra có thể bị bỏ qua. Ngược lại, nếu aspect logging tự động ghi mọi tham số tại adapter, thông tin nhạy cảm có thể bị phát tán. Hai mối quan tâm cần **chính sách riêng**, không gộp chung một biến “ignore all”.

Vì vậy:

- Định nghĩa mục tiêu của từng aspect và **đường bao phủ tối thiểu cần thiết**.
- Chỉ cho phép exclusion đối với các hành vi tùy chọn (ví dụ debug tracing) khi đó là chính sách đã được thiết kế.
- Với enforcement bắt buộc, xác định join point hoặc boundary tin cậy mà cơ chế interception đảm bảo đi qua.
- Viết kiểm thử âm tính (*không nên được advice*) và dương tính (*bắt buộc phải được advice*).
- Xem xét tác động của code generator, cấu hình build và thư viện được nâng cấp.

Không được sử dụng một marker dễ tùy ý gắn để bỏ qua một kiểm tra bắt buộc nếu không có ràng buộc rõ ai được phép đưa marker vào mã.

## 10. Kiểm thử exclusion: chứng minh cả hai phía

Chỉ nhìn log giảm sau khi thêm exclude là chưa đủ: có thể bạn đã loại luôn những thao tác cần quan sát. Kế hoạch kiểm thử nên phản ánh **phạm vi nghiệp vụ có chủ đích**, có ca chồng chéo và thay đổi code sinh.

| Kiểm thử | Kết quả cần xác nhận |
| --- | --- |
| Class thuộc application allowlist, không mang marker loại trừ | Advice chạy đúng số lần mong đợi |
| Class sinh tự động có helper nội bộ | Helper bị bỏ qua theo chính sách |
| Generated API quan trọng vẫn cần quan sát | Boundary được chọn vẫn có advice |
| Thư viện ngoài nhiều lời gọi nội bộ | Không phát event cho implementation detail không cần |
| Adapter gọi API thư viện đã được chọn | Event ở ranh giới vẫn được tạo |
| Logger/exporter được gọi từ advice | Không sinh recursion, stack/event tăng vô hạn |
| Method-level marker trong class hợp lệ | Chỉ method đó bị loại trừ |
| Annotation trên interface và implementation khác nhau | Quy tắc lookup thực tế khớp hợp đồng |
| Method overloaded, overridden, bridge/synthetic | Không bỏ sót hoặc ghi trùng ngoài ý muốn |
| Nhiều aspect trên cùng thao tác | Thứ tự và số bản ghi đúng theo thiết kế |
| Classloader khác hoặc class tái sinh tên mới | Chính sách ownership/provenance vẫn đúng |
| Lời gọi thành công, exception, retry, async | Không làm thay đổi kết quả, lỗi hoặc context |
| Upgrade generator, compiler, thư viện hay runtime | Snapshot phạm vi được đối chiếu và review |

### 10.1. Kiểm thử trước khi cấp quyền phát telemetry thật

Bạn có thể dùng advice test chỉ tăng bộ đếm hoặc ghi sự kiện trong buffer hữu hạn. Đừng để chính bộ đếm/buffer là mục tiêu của pointcut đang thử. Ghi lại danh sách join point được chọn và bị loại bỏ (nếu công cụ hỗ trợ) rồi so sánh với danh sách kỳ vọng.

Nên giữ một số ca mẫu: method nghiệp vụ, method mã sinh, method của thư viện, method telemetry, method annotated, method bridge/synthetic. Sau đó assert cả **đúng vị trí** và **số lần** advice chạy, không chỉ “có bất kỳ log nào”.

### 10.2. Kiểm tra hiệu năng

Với cùng workload, thử các cấu hình:

1. Không áp aspect.
2. Áp pointcut rộng có guard runtime.
3. Thu hẹp pointcut/weaving theo allowlist và exclusions.
4. Bật chính sách logging/tracing thực tế với sink/queue giống vận hành.

Theo dõi throughput, CPU, heap allocation, GC, startup, class load/transform count, kích thước bytecode nếu phù hợp, p50/p95/p99 latency, log/span throughput và dropped events. Đặc biệt đo tại luồng nhiều lời gọi helper, vì “mỗi call chỉ tốn một chút” vẫn có thể trở thành phần lớn overhead khi advice khớp quá rộng.

**Không tự giả định exclusion chắc chắn nhanh hơn**: mức chênh phụ thuộc chi phí matcher, trình biên dịch, JIT, cơ chế instrumentation, cách thiết lập sink và việc advice có thật sự chạy trước đó hay không. Đo trên môi trường và tải tương ứng.

## 11. Quy trình điều tra khi exclusion hoạt động sai

### Trường hợp A — Không loại trừ được mã sinh

Kiểm tra tuần tự:

1. Code sinh thuộc artifact/package/classloader nào khi chạy thật?
2. Quy tắc lọc áp ở transformer/class-level hay ở method-level?
3. Class đã bị weave trước khi rule được áp dụng hay không?
4. Annotation cần kiểm tra có tồn tại trong bytecode/runtime không?
5. Method đang bị bắt là bridge/synthetic/override hoặc proxy wrapper khác so với dự kiến?
6. Có một aspect khác vẫn chạy cùng loại join point?

Tránh vá nhanh bằng một pattern tên quá rộng vì có thể gây coverage gap.

### Trường hợp B — Có log/tracing lặp vô hạn

Tạo biểu đồ chuỗi gọi ngắn nhất từ stack trace hoặc tracing nội bộ (nếu có). Tìm đoạn `advice → logger/tracer/metric → advice`. Sau đó xác định chính xác join point mà infrastructure dùng để quay lại advice, thu hẹp pointcut hoặc loại trừ vùng infrastructure tương ứng. Trong lúc điều tra, hạn chế debug logging dày vì có thể khuếch đại vòng lặp.

### Trường hợp C — Mất log của nghiệp vụ

Kiểm tra xem có loại trừ nguyên class thay vì helper method không; proxy có phải đối tượng mang tên “generated” nhưng trỏ tới target nghiệp vụ không; metadata của annotation được đọc trên interface hay implementation; lời gọi có thật sự đi qua cơ chế interception không; điều kiện runtime có bị treo do guard/`ThreadLocal` không được dọn không.

### Trường hợp D — Chỉ hỏng khi deploy, không hỏng khi test

Khác biệt thường xuất hiện từ classloader, packaging, trình tự nạp class, runtime instrumentation agent, bước code generation hoặc cấu hình pointcut theo environment. Tái hiện bằng build artifact gần production; ghi nhận danh sách lớp đã transform và phiên bản thư viện, tránh lộ các thông tin không cần thiết.

### Trường hợp E — Log ít hơn nhưng CPU vẫn cao

Có thể runtime guard ngăn formatter nhưng việc intercept vẫn xảy ra; hoặc CPU nằm ở pointcut matcher, reflection/metadata và các advice khác. Profiling nên phân biệt thời gian chặn lời gọi, quyết định policy, thực thi advice và viết sink. Nếu phù hợp, chuyển exclusion sang tầng weaving/selection để tránh overhead trước khi advice chạy.

## 12. Quản lý chính sách exclusion theo thời gian

Các hệ thống lớn có nhiều generator, module và integration nên xem exclusion như một phần cấu hình kiến trúc có thể kiểm tra, không phải danh sách chuỗi khó giải thích.

Một bản ghi chính sách hữu ích gồm:

| Trường | Ví dụ ý nghĩa |
| --- | --- |
| **Target** | Package/class/module/artifact/method hoặc nhóm join point |
| **Aspect áp dụng** | Logging, timing, tracing, enforcement cụ thể |
| **Decision** | Include hoặc exclude |
| **Reason** | Tránh recursion, loại helper tần suất cao, dữ liệu nhạy cảm, v.v. |
| **Owner** | Nhóm chịu trách nhiệm điều chỉnh |
| **Evidence** | Benchmark, trace, lỗi đã tái hiện hoặc test coverage |
| **Review trigger** | Nâng cấp generator, thay classloader, thêm advice mới |

Không cần hệ thống cấu hình phức tạp nếu ứng dụng nhỏ. Nhưng dù dùng code hay cấu hình, rule nên dễ đọc, có test và có cách xem tập join point cuối cùng sau khi kết hợp include/exclude.

Một chính sách có thể bắt đầu như sau (mã giả):

```text
Policy: diagnostic-timing
  include:
    - application use-case boundaries
    - selected outbound integration boundaries
  exclude:
    - telemetry infrastructure
    - generated mapping helpers
    - external library internals
    - methods explicitly marked skip-diagnostic-timing
  runtime-guard:
    - suppress recursion from emission path
  validation:
    - business coverage cases
    - no recursive telemetry cases
    - throughput and p99 comparison
```

Đây là ví dụ thiết kế, **không phải cú pháp cấu hình cụ thể**. Khi sử dụng cần chuyển rule sang khả năng thực tế của từng công cụ AOP và bổ sung chi tiết tên lớp/phạm vi thuộc ứng dụng.

## 13. Checklist review trước khi triển khai

- [ ] Xác định chính xác mục đích của aspect và join point cần quan sát.
- [ ] Có allowlist rõ ràng, không mặc định chặn toàn bộ classpath.
- [ ] Exclusion dựa trên ownership, vai trò và lý do cụ thể.
- [ ] Mã sinh được phân biệt giữa helper nội bộ với boundary quan trọng.
- [ ] Thư viện ngoài được quan sát ở ranh giới cần thiết, không tự động chặn mọi implementation detail.
- [ ] Logger, tracer, metric exporter và helper instrumentation không gọi vòng lại cùng advice.
- [ ] Đã cân nhắc exclude ở weaving/class-selection thay cho chỉ guard runtime.
- [ ] Marker annotation có retention, phạm vi và cách kế thừa/override đúng.
- [ ] Proxy, self-invocation, classloader và bridge/synthetic method đã được test theo cơ chế thực tế.
- [ ] Exclusion cho logging/tracing không vô tình tắt aspect enforcement bắt buộc.
- [ ] Có test dương tính và âm tính cho danh sách join point.
- [ ] Benchmark đo cùng workload, gồm throughput, CPU, GC, p99 và số event/log.
- [ ] Các rule có lý do, owner và điều kiện cần review khi thay đổi build/dependencies.

## Kết luận

Trong AOP, **phạm vi áp dụng advice** quan trọng không kém logic advice. Mã sinh, thư viện ngoài và công cụ quan sát đều có thể cần loại trừ để tránh recursion, overhead và dữ liệu trùng; nhưng chính những thành phần đó đôi khi lại chứa ranh giới quan trọng cần được quan sát hoặc bảo vệ.

Cách làm bền vững là bắt đầu từ mục tiêu của từng aspect, chọn allowlist vừa đủ, loại trừ chính xác ở tầng mà công nghệ AOP hỗ trợ và dùng guard runtime cho các điều kiện thật sự động. Sau cùng phải xác minh cả hai vế: **những join point cần advice vẫn được xử lý**, và **những vùng bị loại trừ không còn gây tác dụng phụ hoặc overhead không cần thiết**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-integration-deployment-007">Kiểm tra tương thích với kiểm thử, đo độ bao phủ và gỡ lỗi</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra tương thích với kiểm thử, đo độ bao phủ và gỡ lỗi trong AOP

Aspect-oriented programming (AOP) đưa các mối quan tâm xuyên suốt như kiểm soát truy cập, audit, đo thời gian, quản lý tài nguyên và xử lý lỗi vào những điểm thực thi (*join point*). Vì phần xử lý này có thể xuất hiện ở lớp proxy, chuỗi interceptor hoặc mã bytecode đã được biến đổi (*weaving*), một phép kiểm thử “nghiệp vụ trả đúng kết quả” **chưa đủ** để chứng minh advice được áp dụng chính xác. Ngược lại, việc công cụ đo *code coverage* báo 100% trên phương thức đích cũng không có nghĩa mọi điểm cần chặn đã được chặn.

Bài này cung cấp cách kiểm tra tương thích AOP với bộ kiểm thử, công cụ đo độ bao phủ và trình gỡ lỗi; đồng thời chỉ ra cách xác nhận **hành vi của advice, phạm vi pointcut và kết quả sau khi instrument** ở cả môi trường kiểm thử lẫn bản triển khai thực tế. Phạm vi là AOP nói chung, với các cơ chế proxy và weaving phổ biến ở mức khái niệm.

## 1. Ba câu hỏi phải kiểm chứng độc lập

1. **Chức năng còn đúng không?** Cùng yêu cầu nghiệp vụ phải giữ đúng kết quả, tác dụng phụ được phép, ngoại lệ, thứ tự thực hiện và ràng buộc dữ liệu.
2. **Advice có chạy đúng chỗ và đúng số lần không?** Pointcut chọn đúng join point; advice chạy khi bắt buộc và không chạy ở ngoài phạm vi; đường lỗi và lời gọi lồng nhau được xét đúng.
3. **Bằng chứng đo và gỡ lỗi có đáng tin không?** Công cụ coverage có nhìn đúng mã được chạy không, breakpoint có khớp vị trí bytecode không, stack trace có phản ánh lớp trung gian và thứ tự biến đổi hay không?

Ba câu hỏi này liên quan nhau nhưng không thay thế nhau. Ví dụ một hàm `transfer()` trả đúng số tiền có thể vẫn thiếu advice audit trên một đường gọi nội bộ. Test nghiệp vụ thành công sẽ không tự phát hiện lỗi đó nếu không khẳng định sự kiện audit phải được ghi.

### 1.1. Những thành phần ảnh hưởng đến kiểm thử

| Thành phần | Điều cần quan sát | Sai lệch thường gặp |
|---|---|---|
| Join point | Vị trí lời gọi hoặc thực thi thật sự | Nhầm caller với callee; bỏ qua đường gọi nội bộ |
| Pointcut | Điều kiện chọn điểm chặn | Quá rộng, quá hẹp, sai overload, sai điều kiện động |
| Advice | Before, after, after throwing, around | Chạy thiếu hoặc thừa; thay đổi kết quả/ngoại lệ |
| Proxy / interceptor chain | Đường dispatch và thứ tự mắt xích | Test gọi thẳng target nên không chạy advice |
| Weaving | Mã được chèn ở compile/load time | Bản test và bản chạy dùng class khác cách biến đổi |
| Instrumentation của coverage | Bộ đếm nhánh/dòng lệnh được chèn vào mã | Biến đổi bytecode không khớp, bỏ sót mã sinh |
| Debug metadata | Line number, method name, source mapping | Breakpoint lệch, stack frame do mã sinh tạo ra |
| Class loader / runtime agent | Class thực tế được nạp và biến đổi | Agent không chạy, thứ tự biến đổi khác môi trường |

Không phải mọi hệ thống AOP đều dùng proxy hoặc reflection. Một số công cụ biến đổi bytecode để advice thực thi ngay tại điểm được chọn; bởi vậy cách kiểm thử phải bám sát **cơ chế đang chạy thật**, không suy diễn từ một sơ đồ chung.

## 2. Kiểm thử ở đúng ranh giới can thiệp

### 2.1. Test phương thức đích và test lời gọi qua AOP là hai phép thử khác nhau

Giả sử một đối tượng `Ledger` thực thi `debit()`. Có thể có hai đường gọi:

```text
Unit test trực tiếp
    ↓
Ledger.debit()
    ↓
Kết quả nghiệp vụ

Integration test đi qua AOP
    ↓
Proxy / mã đã weave
    ↓
Advice kiểm tra chính sách
    ↓
Ledger.debit()
    ↓
Advice sau thực thi / khi lỗi
    ↓
Kết quả nghiệp vụ + bằng chứng advice
```

Khi dùng proxy, test chỉ khởi tạo `new Ledger()` và gọi trực tiếp có thể bỏ qua cơ chế chặn. Test này vẫn hữu ích để kiểm chứng logic thuần túy, nhưng **không xác minh việc proxy đã được gắn vào hệ thống**. Với weaving, đối tượng tạo bằng `new` có thể mang mã đã chèn nếu class được nạp bằng đường weaving tương ứng; phải xác minh cách nạp class và classpath thay vì giả định.

Một kế hoạch kiểm thử tốt giữ cả hai loại:

- **Kiểm thử logic nghiệp vụ:** ngắn, cô lập, dễ chẩn đoán; không nhất thiết cần khởi chạy hạ tầng AOP.
- **Kiểm thử hợp đồng advice:** kiểm tra logic advice với ngữ cảnh giả lập/harness tối thiểu; chỉ phù hợp khi advice không phụ thuộc những đặc tính runtime chưa được giả lập.
- **Kiểm thử tích hợp AOP:** lấy đối tượng qua đúng đường được instrument hoặc kích hoạt weaving thật; xác minh pointcut, dispatch, thứ tự advice và đường lỗi.
- **Kiểm thử end-to-end hoặc tải:** xác minh chuỗi hành vi trên các biên thực tế, tính tương thích khi đóng gói/chạy và overhead đo được ở tải đại diện.

### 2.2. Test double, mock và spy có thể thay đổi đường gọi

Test double giúp cách ly phụ thuộc nhưng cũng có thể làm biến mất hoặc đổi thứ tự AOP:

- Mock của **target** đặt phía sau proxy: có thể kiểm tra advice rồi xác nhận target được gọi.
- Mock của **đối tượng proxy** đưa thẳng cho caller: có thể thay thế luôn lớp interception; khi ấy test không chứng minh advice chạy.
- Spy hoặc subclass có thể ảnh hưởng thứ tự override/dispatch; các điểm chặn liên quan `final`, `private`, default method và bridge method phụ thuộc kỹ thuật instrument.
- Callback giả lập có thể không tái hiện đúng thread, class loader, exception wrapper hoặc lifecycle context.

Khi cần kiểm chứng advice thật, phải chỉ rõ **đối tượng nào được instrument, object nào được thay bằng test double và lời gọi đi qua tuyến nào**. Không coi mọi mock-based test là kiểm thử tích hợp AOP.

### 2.3. Ma trận hành vi tối thiểu

| Tình huống | Kỳ vọng cần assert |
|---|---|
| Lời gọi khớp pointcut | Advice được chạy; target chạy theo hợp đồng |
| Lời gọi không khớp | Advice không chạy; nghiệp vụ không đổi |
| Phương thức overload | Chọn đúng chữ ký theo chính sách |
| Điều kiện động đúng/sai | Chặn theo dữ liệu thực tế ở từng invocation |
| Target trả kết quả bình thường | After returning/around giữ hoặc biến đổi kết quả đúng quy tắc |
| Target ném ngoại lệ | After throwing/finally xử lý đúng; không vô tình nuốt lỗi |
| Advice ném ngoại lệ | Tác động lên target và caller đúng hợp đồng |
| Around bỏ qua `proceed()` | Target không chạy khi được phép short-circuit |
| Around gọi `proceed()` nhiều lần | Đếm đúng lần gọi đích, side effect và cơ chế retry |
| Nhiều advice | Đúng thứ tự before, after, finally, exception path |
| Gọi trong cùng đối tượng | Xác nhận có hoặc không bị chặn tùy cơ chế |
| Async/concurrent | Ngữ cảnh được truyền, cô lập và dọn đúng vòng đời |
| Tạo mới đối tượng/class loader khác | Điểm can thiệp vẫn được áp dụng theo cấu hình thực |

Mỗi dòng nên có ít nhất một test tích cực và một test phủ định liên quan, đặc biệt đối với chính sách bảo mật, audit hoặc cleanup bắt buộc.

## 3. Ví dụ Java 17+: kiểm tra kết quả, số lượt advice và self-invocation

Ví dụ sau dùng **JDK dynamic proxy** cho interface `Counter`. Handler đóng vai trò một advice quan sát; nó tăng các bộ đếm khi đi vào phương thức và khi lời gọi hoàn tất hoặc ném lỗi. Mã đồng thời kiểm tra `add()` gọi `value()` **trực tiếp trên chính target**: lời gọi nội bộ này không đi qua proxy nên không làm tăng bộ đếm của `value()`.

Tạo tệp `AopCompatibilityDemo.java`:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class AopCompatibilityDemo {
    interface Counter {
        int add(int delta);
        int value();
    }

    static final class CounterImpl implements Counter {
        private int current;

        @Override
        public int add(int delta) {
            if (delta < 0) {
                throw new IllegalArgumentException("negative delta");
            }
            current += delta;
            return value(); // gọi trên chính target, không qua proxy
        }

        @Override
        public int value() {
            return current;
        }
    }

    static final class Probe {
        int addVisits;
        int valueVisits;
        int returns;
        int failures;
    }

    static Counter instrument(Counter target, Probe probe) {
        return (Counter) Proxy.newProxyInstance(
            Counter.class.getClassLoader(),
            new Class<?>[] { Counter.class },
            (proxy, method, args) -> {
                if (method.getName().equals("add")) {
                    probe.addVisits++;
                } else if (method.getName().equals("value")) {
                    probe.valueVisits++;
                }

                try {
                    Object result = method.invoke(target, args);
                    probe.returns++;
                    return result;
                } catch (InvocationTargetException exception) {
                    probe.failures++;
                    throw exception.getCause();
                }
            }
        );
    }

    static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        CounterImpl target = new CounterImpl();
        Probe probe = new Probe();
        Counter proxy = instrument(target, probe);

        // Kiểm thử trực tiếp không kích hoạt handler.
        check(target.value() == 0, "initial state");
        check(probe.valueVisits == 0, "direct call bypass");

        // add() gọi value() nội bộ: chỉ add() qua proxy.
        check(proxy.add(7) == 7, "add result");
        check(probe.addVisits == 1, "add intercepted once");
        check(probe.valueVisits == 0, "self call bypass");

        // Gọi value() từ ngoài thông qua proxy.
        check(proxy.value() == 7, "proxied value");

        try {
            proxy.add(-1);
            throw new AssertionError("exception was expected");
        } catch (IllegalArgumentException expected) {
            check(
                expected.getMessage().equals("negative delta"),
                "original exception preserved"
            );
        }

        check(proxy.value() == 7, "state after failure");
        check(probe.addVisits == 2, "add advice count");
        check(probe.valueVisits == 2, "value advice count");
        check(probe.returns == 3, "success path count");
        check(probe.failures == 1, "exception path count");

        System.out.println("Business: OK");
        System.out.println("Advice add: " + probe.addVisits);
        System.out.println("Advice value: " + probe.valueVisits);
        System.out.println("Advice success: " + probe.returns);
        System.out.println("Advice failure: " + probe.failures);
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac AopCompatibilityDemo.java
java AopCompatibilityDemo
```

Kết quả stdout chính xác khi mọi kiểm tra đạt:

```text
Business: OK
Advice add: 2
Advice value: 2
Advice success: 3
Advice failure: 1
```

**Giải thích số đếm:**

1. `target.value()` chạy trực tiếp nên không kích hoạt handler.
2. `proxy.add(7)` kích hoạt handler ở `add` một lần; lời gọi `value()` bên trong `CounterImpl.add` đi thẳng đến target và không làm tăng `valueVisits`.
3. `proxy.value()` kích hoạt handler ở `value` một lần.
4. `proxy.add(-1)` kích hoạt `add` lần hai rồi target ném `IllegalArgumentException`; handler mở `InvocationTargetException` để bảo toàn nguyên nhân lỗi.
5. `proxy.value()` cuối cùng kích hoạt `value` lần hai; hai lời gọi `value` qua proxy cùng `add(7)` cho ba lần hoàn thành bình thường. Lời gọi `add(-1)` tạo một lượt lỗi.

Các bộ đếm cố ý dùng `int` vì demo chạy tuần tự. Nếu cần kiểm thử concurrent calls, thay bộ đếm bằng cấu trúc đồng bộ thích hợp hoặc lưu sự kiện theo invocation để khẳng định thứ tự mà không tạo data race. Mã minh họa không tự xử lý hợp đồng `equals/hashCode/toString` của dynamic proxy và không đại diện cho mọi cách weaving.

### 3.1. Những kết luận được phép và không được phép rút ra

Được phép kết luận: **với chương trình trên**, khi đi qua JDK dynamic proxy, handler đã được gọi đúng số lần; direct call và self-invocation trên target đã đi vòng qua handler; ngoại lệ đích được chuyển tiếp đúng.

Không được phép kết luận: mọi AOP đều bỏ qua self-invocation; mọi proxy đều dùng reflection; advice đã được instrument đầy đủ ở build production; công cụ code coverage sẽ hiển thị đúng mọi dòng mã biến đổi. Những điều đó cần được kiểm tra riêng trong môi trường AOP thực tế.

## 4. Phân biệt code coverage và độ bao phủ AOP

**Code coverage** thường đánh giá số dòng, nhánh hoặc instruction của mã chương trình đã chạy trong phép thử. **Join-point/advice coverage** lại trả lời các câu hỏi chức năng AOP: bao nhiêu điểm cần chặn được instrument; ở điểm nào điều kiện pointcut đúng; các nhánh của advice nào đã được thử; đường gọi không cần chặn có bị chặn nhầm không.

| Phép đo | Ví dụ câu hỏi | Giới hạn |
|---|---|---|
| Line coverage | Dòng xử lý trong `debit()` đã chạy chưa? | Không khẳng định lời gọi đã đi qua advice |
| Branch coverage | Cả nhánh được phép và bị từ chối đã chạy chưa? | Không đảm bảo đã thử mọi tình huống join point |
| Instruction coverage | Những bytecode instruction nào thực thi? | Mã sinh có thể có vị trí nguồn không rõ |
| Method coverage | Phương thức `transfer()` có từng được chạy? | Không thể hiện số lần interception mong đợi |
| Join-point coverage | Những điểm được chọn bắt buộc có chặn thật không? | Cần tập mong đợi do thiết kế/kiểm thử định nghĩa |
| Pointcut negative coverage | Những phương thức không thuộc phạm vi có bị advice chạy nhầm không? | Cần ca phủ định, không suy ra từ line coverage |
| Advice-path coverage | Before, normal return, exception, short-circuit, retry đã được thử? | Cần instrument hoặc assertion riêng về advice |
| Outcome / contract coverage | Caller nhìn thấy đúng kết quả, ngoại lệ và side effect? | Không được thay bằng bộ đếm mà không xét chức năng |

Một điểm quan trọng: **100% line coverage không phải bằng chứng pointcut đúng**. Các dòng trong target có thể được gọi trực tiếp khi hệ thống production lẽ ra phải đi qua proxy; các advice có thể chỉ chạy ở test khác hoặc bị loại khỏi báo cáo coverage. Ngược lại, chỉ số advice hit cao cũng không chứng minh đủ trường hợp lỗi và phủ định.

### 4.1. Thiết lập ma trận bao phủ join point

Ví dụ bảng kiểm thử tự quản lý:

| Điểm thực thi / đường gọi | Chính sách mong đợi | Test tích cực | Test phủ định | Bằng chứng |
|---|---|---|---|---|
| `payment.authorize()` từ biên công khai | Phải kiểm tra quyền | Có | Không đủ quyền | Assert quyết định và audit |
| `payment.capture()` thành công | Phải ghi audit | Có | Input không hợp lệ | Assert số bản ghi và nội dung |
| `payment.capture()` ném lỗi | Phải ghi lỗi/cleanup khi quy định | Có | Không có lỗi | Assert đường exception/finally |
| `payment.balance()` | Không chạy audit thay đổi | Có | Gọi lặp lại | Assert không có audit |
| `payment.capture()` gọi nội bộ | Tùy cơ chế AOP, nhưng chính sách phải rõ | Có | Gọi bên ngoài | Assert đường interception thực tế |

Đây là bảng phương pháp; tên phương thức minh họa. “Độ bao phủ join point 100%” chỉ có ý nghĩa nếu **tập điểm bắt buộc được định nghĩa đầy đủ**, bao gồm những đường gọi làm phát sinh tác dụng phụ. Cần kiểm tra cả điểm bị chặn nhầm để tránh một pointcut quá rộng.

### 4.2. Không đánh đồng lời gọi và lần thực thi

Một join point **method call** được xác định tại nơi gọi; một join point **method execution** được xác định tại nơi phương thức chạy. Trong hệ thống có nhiều caller, một execution có thể được đi vào từ nhiều call site; trong retry/recursive calls, số lần gọi và số lần thực thi có thể khác nhau. Khi báo cáo coverage, cần ghi rõ đơn vị đếm: call site, execution, event nghiệp vụ, lần vào interceptor hay số lần target chạy.

Nếu advice around có thể không gọi hoặc gọi `proceed()` nhiều lần, số “advice hit” càng không đồng nhất với “số lượt nghiệp vụ hoàn thành”.

## 5. Vì sao weaving và công cụ coverage có thể tương tác?

Các công cụ coverage Java thường chèn logic đếm hoặc dùng runtime agent để quan sát bytecode. Một cơ chế AOP có thể chèn advice vào cùng class. **Hai phép biến đổi có thể tác động lên cùng bytecode**, vì vậy thứ tự, thời điểm và phạm vi instrument ảnh hưởng khả năng báo cáo hoặc gỡ lỗi.

### 5.1. Các giai đoạn có thể xuất hiện

```text
Mã nguồn .java
    │
    ▼
Biên dịch (javac)
    │
    ▼
Class bytecode ban đầu
    │
    ├── [A] Weave advice khi biên dịch / hậu biên dịch
    │        └── Coverage instrument khi chạy
    │
    ├── [B] Coverage instrument offline
    │        └── Weave trên bytecode đã instrument
    │
    └── [C] Nạp class qua một hoặc nhiều agent/transformer
             └── thứ tự biến đổi phụ thuộc cấu hình runtime
    │
    ▼
Bytecode cuối cùng JVM thực thi
    │
    ▼
Dữ liệu coverage + stack trace + kết quả kiểm thử
```

Đây là các **khả năng triển khai**; không có thứ tự chung đúng cho tất cả công cụ. Một số công cụ không hỗ trợ mọi kết hợp hoặc có quy tắc riêng về instrument offline, class loader, retransform và định danh bytecode. Cần dựa vào tài liệu của công cụ đang dùng và kiểm tra pipeline thật.

### 5.2. Các dấu hiệu xung đột đáng điều tra

- Coverage trên test class bị trống hoặc thấp bất thường dù breakpoint thấy code đã chạy.
- Cùng một file nguồn có report khác nhau giữa test thường và test có weaving.
- Class đã instrument trước đó bị báo lỗi khi instrument lần nữa, hoặc xuất hiện lỗi verify/link.
- Các phương thức tổng hợp (*synthetic*), bridge, accessor hoặc code sinh ra xuất hiện trong report làm lệch tỷ lệ mong đợi.
- Không khớp checksum/class id hoặc thông tin debug của class được báo cáo với bytecode thật trong runtime.
- Class đã thay đổi nhưng report dùng bản cũ; cache, incremental build hoặc thứ tự agent là một khả năng cần kiểm chứng.
- Breakpoint đặt trên source không dừng ở vị trí kỳ vọng, nhưng stack trace cho thấy code đã biến đổi vẫn chạy.

Các triệu chứng này **không tự động chứng minh lỗi do AOP**. Có thể là build profile, classpath, agent chưa được bật, khác JDK, test bị bỏ qua hoặc report filter.

### 5.3. Cách chẩn đoán có kiểm soát

Thực hiện bốn cấu hình trên cùng bài test và cùng class mục tiêu nếu bộ công cụ hỗ trợ:

| Cấu hình | AOP | Coverage | Mục tiêu |
|---|---|---|---|
| Baseline | Tắt | Tắt | Hợp đồng nghiệp vụ gốc |
| AOP only | Bật | Tắt | Advice và pointcut hoạt động đúng |
| Coverage only | Tắt | Bật | Report coverage bình thường |
| Combined | Bật | Bật | Kiểm chứng tương thích và độ trung thực của report |

Ghi lại **JDK, tham số JVM, phiên bản công cụ, tập class được biến đổi, class loader, cách instrument và bản build**. Nếu chỉ cấu hình combined bị lỗi, tiếp tục kiểm tra thứ tự biến đổi/agent theo tài liệu. Nếu AOP only đã sai, sửa phạm vi pointcut hoặc trình tự advice trước; không sửa report coverage để che lỗi.

### 5.4. Source mapping và lọc báo cáo

Bytecode weave hoặc sinh proxy có thể mang line-number table, frame và phương thức tổng hợp khác với mã nguồn gốc. Coverage report có thể lọc một số phần sinh ra hoặc tính các nhánh không có dòng nguồn trực tiếp. Do đó:

- Đối chiếu số liệu coverage với **bản class thực sự được nạp**.
- Phân biệt code tự viết, advice tự viết, mã sinh do compiler và mã do công cụ instrument.
- Nếu cần loại trừ mã sinh khỏi KPI, ghi rõ tiêu chí exclude và kiểm tra liệu exclude có vô tình che advice quan trọng không.
- Không tự tăng tỷ lệ coverage bằng cách bỏ cả những module AOP cần test.
- Khi classpath có nhiều bản class trùng tên, phải xác minh bản nào được loader chọn.

Một số công cụ như JaCoCo hỗ trợ line/branch coverage cho bytecode Java, nhưng chi tiết tương thích với biến đổi mã phải xác nhận theo phiên bản và cấu hình đang sử dụng; tên công cụ không bảo đảm tự giải quyết mọi trường hợp weaving.

## 6. Kiểm tra tính tương thích khi đóng gói và triển khai

Môi trường kiểm thử có thể khác production ở những điểm quan trọng:

1. **Compile-time vs load-time weaving:** Nếu test chạy class chưa weave nhưng production chạy class đã weave, kết quả unit test không chứng minh advice production. Điều ngược lại cũng đúng.
2. **Proxy được dựng ở đâu:** Test trực tiếp tạo target không đại diện cho object thực tế được bọc trong cấu hình ứng dụng.
3. **Agent và JVM flags:** Runtime thiếu agent hoặc cấu hình mở truy cập/module có thể khiến việc can thiệp không thực hiện hoặc báo lỗi.
4. **Class loader:** Môi trường plugin/module, test runner và runtime có thể nạp lớp qua loader khác nhau; matcher, cache metadata và cơ chế weaving cần được kiểm tra.
5. **Classpath và thứ tự phụ thuộc:** Version mismatch, nhiều class cùng tên hoặc công cụ biến đổi lặp làm kết quả khác bản test.
6. **Chế độ tối ưu build:** Mã sinh/obfuscation/shading có thể đổi tên, metadata hoặc debug symbols mà pointcut hay debugger dựa vào.
7. **Khởi tạo và lifecycle:** Advice có thể đọc dữ liệu chưa sẵn sàng ở startup, static init hoặc constructor; không được giả định các join point đó luôn được hỗ trợ.
8. **Bất đồng bộ:** Invocation context có thể không đi theo thread hoặc callback nếu chưa có propagation; test đồng bộ không bắt được lỗi này.

Nên có ít nhất một **smoke test dùng đúng artifact chạy thực tế** (class/jar và tham số runtime phù hợp) để kiểm chứng một join point đại diện: advice khớp, target chạy đúng, đường lỗi đúng và log/metric cần thiết xuất hiện. Test đó không thay thế kiểm thử đầy đủ nhưng giúp phát hiện khác biệt giữa pipeline test và triển khai.

### 6.1. Kiểm tra điều kiện tiên quyết của weaving

Với triển khai có weaving, tài liệu cấu hình cần trả lời được:

- Weave ở bước biên dịch, sau biên dịch hay lúc class loading?
- Class/jar nào được biến đổi, class nào bị exclude?
- Những thư viện nào chứa runtime của advice và chúng có mặt trong artifact cuối không?
- Dữ liệu debug (line numbers, local variables, source file) có được giữ?
- Agent coverage và agent weaving được nạp theo thứ tự nào, và tổ hợp đó có được hỗ trợ?
- Có dự phòng nếu không instrument được một class bắt buộc hay không?
- Báo cáo build có bằng chứng rằng các class cần weave đã được xử lý chưa?

Không được coi một log kiểu “weaving initialized” là bằng chứng mọi join point mong muốn đã được can thiệp. Test hành vi tại join point vẫn là nguồn bằng chứng quyết định.

## 7. Gỡ lỗi khi luồng điều khiển đi qua advice

### 7.1. Stack trace có thể xuất hiện các khung trung gian

Ví dụ minh họa, **không phải stack trace cố định của Java**:

```text
Caller
  → generated proxy / dispatch handler
    → interceptor A (before)
      → interceptor B (around)
        → target method
      ← interceptor B (after / exception)
    ← interceptor A (after)
  ← Caller
```

Trong exception path, khi một advice bắt và chuyển đổi exception, stack trace có thể chỉ rõ vị trí ném mới thay vì vị trí tạo lỗi đầu tiên. Các wrapper reflection như `InvocationTargetException` hoặc proxy wrapper theo quy tắc Java cũng có thể xuất hiện nếu không được xử lý theo hợp đồng.

Cách đọc stack trace có hệ thống:

1. Xác định exception gốc, `cause` và các exception bị suppressed.
2. Tìm frame ứng dụng đầu tiên liên quan lỗi thực; phân biệt frame của advice, proxy và target.
3. Xác định lời gọi target đã xảy ra chưa, xảy ra mấy lần, và có advice nào short-circuit không.
4. Đối chiếu line number với artifact bytecode đang chạy.
5. Kiểm tra exception có bị advice nuốt, bọc thêm, đổi kiểu hoặc ghi đè trong `finally` không.

**Không kết luận “lỗi nằm ở proxy” chỉ vì frame proxy nằm gần đầu stack trace.** Proxy có thể chỉ là nơi lỗi đi qua.

### 7.2. Đặt breakpoint đúng vị trí

Một chuỗi breakpoint có ích cho đường gọi qua proxy là:

```text
(1) Caller trước khi gọi
(2) Proxy/handler hoặc interception entry point
(3) Điều kiện pointcut động (nếu có)
(4) Advice trước proceed
(5) Target method
(6) Advice sau proceed / catch / finally
(7) Caller nhận kết quả hoặc lỗi
```

Nếu breakpoint ở advice không dừng, cần phân biệt:

- Test đang gọi trực tiếp target?
- Join point có thuộc phạm vi pointcut?
- Advice có được instrument và nạp chưa?
- Breakpoint có ánh xạ đúng bytecode đang chạy?
- Đường execution đã đi qua advice khác short-circuit chưa?

Nếu breakpoint ở target dừng nhưng không có frame của advice, có thể lời gọi đi vòng proxy hoặc class thực tế không được weave. Nếu advice dừng mà target không dừng, có thể advice around chặn lời gọi theo thiết kế hoặc đã ném lỗi trước `proceed()`.

### 7.3. Thông tin debug khi biến đổi bytecode

Khi có weaving, các bảng như `LineNumberTable` và `LocalVariableTable` ảnh hưởng khả năng trình gỡ lỗi ánh xạ bytecode về nguồn. Dùng công cụ JDK thích hợp để xem class đã build:

```bash
javap -c -l -p com.example.Demo
```

Lệnh này chỉ là **ví dụ cú pháp**: class `com.example.Demo` phải tồn tại trên classpath, và khi cần phải thêm `-classpath <đường-dẫn-class-hoặc-jar>`. `javap` cho thấy bytecode, line numbers nếu được biên dịch kèm debug information, và cấu trúc phương thức; nó không tự chứng minh JVM đang dùng đúng file class vừa xem. Muốn xác minh class runtime, cần đối chiếu class loader, code source và bản artifact đã nạp.

Việc bật/giữ metadata debug có thể cải thiện khả năng theo dõi nhưng phải nhất quán với cách build; obfuscation hoặc stripping metadata có thể khiến dòng nguồn thiếu hoặc lệch. Không chỉnh mã production theo stack frame do mã sinh nếu chưa xác nhận source mapping.

### 7.4. Log và trace phải gắn với invocation cụ thể

Khi instrument nhiều advice, log “before” và “after” thiếu định danh có thể xen kẽ giữa các thread, làm người đọc hiểu sai thứ tự. Nên thêm correlation ID/trace ID phù hợp, tên join point và trạng thái outcome; khi cần, ghi một sự kiện rõ ràng cho đường exception.

Tuy nhiên, công cụ chẩn đoán tự làm thay đổi tải hệ thống. Không stringify toàn bộ đối số, stack trace hoặc ghi I/O đồng bộ ở mọi hot path chỉ để tìm một lỗi hiếm. Dùng log có điều kiện, sampling chẩn đoán khi chính sách cho phép, giới hạn dữ liệu nhạy cảm và quan sát tác động performance.

## 8. Gỡ lỗi các tình huống AOP khó

### 8.1. Self-invocation và gọi vòng qua proxy

Lời gọi từ một method đến method khác trên cùng target thường không đi qua **proxy bên ngoài**; ví dụ Java ở trên chứng minh trường hợp này. Với weaving, một số self-invocation có thể vẫn là join point hợp lệ. Vì vậy cần kiểm tra:

- Caller giữ tham chiếu đến proxy hay target?
- Join point là method call hay method execution?
- Method có thể được cơ chế instrument chặn không?
- Việc bỏ qua advice là một phần hợp đồng hay là lỗi bảo mật/quan sát?

Nếu advice kiểm tra quyền bắt buộc phải bao phủ đường gọi nội bộ, cần chọn ranh giới kiểm soát và cơ chế phù hợp, không chỉ dựa vào test gọi từ ngoài.

### 8.2. Multiple advice và thứ tự quanh `proceed()`

Với hai advice around, kết quả thường có cấu trúc lồng nhau:

```text
A before
  B before
    target
  B after
A after
```

Nếu B không gọi `proceed()`, target không chạy; nếu B retry, target có thể chạy nhiều lần. Kiểm thử cần assert **thứ tự, số lần và outcome**, chứ không chỉ đếm code line. Với `try/finally`, cleanup chạy khi có lỗi; còn “after returning” chỉ dành cho hoàn thành bình thường. Đừng thay tương đương hai loại này khi sửa lỗi.

### 8.3. Exception wrapping

Reflection `Method.invoke` đóng gói lỗi ném bởi target trong `InvocationTargetException`. Một handler có thể mở `getCause()` để duy trì loại lỗi gốc, nhưng giao diện proxy còn có quy tắc riêng đối với checked exception chưa khai báo. Trong kiểm thử:

- Assert loại exception mà **caller thực sự nhận**.
- Assert `cause`/suppressed khi hợp đồng yêu cầu.
- Đảm bảo advice exception handling không biến lỗi thành thành công giả.
- Thử trường hợp lỗi do advice và lỗi do target, vì cách xử lý có thể khác nhau.

### 8.4. Concurrency và context propagation

Nếu advice dùng `ThreadLocal`, test một luồng có thể luôn qua. Trong môi trường thread pool:

- Giá trị cũ có thể rò sang tác vụ mới nếu không dọn.
- Context không tự xuất hiện ở thread khác trong mọi mô hình async.
- Một invocation state dùng chung có thể khiến chỉ số interceptor hoặc đối số bị ghi đè.
- Thứ tự log không tương đương thứ tự nghiệp vụ khi nhiều thread.

Kiểm thử đồng thời cần xác nhận cách ly định danh, cleanup sau exception, propagation khi chuyển thread và tính đúng đắn của counter. Không dùng số đếm `int` không đồng bộ trong một demo nhiều luồng rồi diễn giải sai kết quả như lỗi của AOP.

### 8.5. Điểm chặn trùng lặp

Một ứng dụng có thể bị áp advice hai lần do cả proxy lẫn weaving, nhiều lớp decorator, cấu hình lặp hoặc hai chain khác nhau. Triệu chứng gồm audit trùng, metric bị nhân đôi, `proceed()` lồng bất ngờ và stack trace dài. Cần đối chiếu identity của object, đường dispatch và danh sách cơ chế can thiệp thực tế. Kiểm thử “mỗi sự kiện nghiệp vụ tạo đúng số side effect” giúp phát hiện tốt hơn một test chỉ nhìn kết quả trả về.

## 9. Quy trình kiểm chứng tương thích cho dự án

### 9.1. Trước khi thay đổi cấu hình AOP

1. Ghi rõ hợp đồng chức năng của từng advice: trường hợp phải chạy, được bỏ qua, thứ tự, kết quả, side effects và lỗi.
2. Lập danh sách pointcut dự kiến gồm join point bắt buộc và những điểm không được can thiệp.
3. Xác nhận cách triển khai: proxy, generated subclass, weaving hoặc kết hợp; xác nhận điểm giới hạn của cơ chế.
4. Ghi lại JDK, pipeline biên dịch, classpath, agent, bộ đo coverage và cách chạy test.
5. Có baseline gồm test nghiệp vụ, test advice, test tích hợp, code coverage và một phép đo hành vi/hiệu năng đại diện.

### 9.2. Sau khi thay đổi

1. Chạy test nghiệp vụ thuần để phát hiện hồi quy ngoài AOP.
2. Chạy test hợp đồng advice với cả thành công và ngoại lệ.
3. Chạy test tích hợp trên **đúng đường được instrument**, kiểm tra cả join point chọn và không chọn.
4. So coverage line/branch/instruction với baseline; điều tra thay đổi bất thường do bytecode biến đổi.
5. Xem advice-hit/pointcut matrix, bởi chỉ số line coverage không đo được điều kiện chặn.
6. Chạy tổ hợp AOP + coverage và xác nhận không có xung đột agent/bytecode.
7. Chạy smoke test artifact triển khai thật với agent/weaving runtime tương ứng.
8. Khi thất bại, dùng breakpoint ở cả interception entry và target, đối chiếu class loader/bytecode rồi sửa đúng nguyên nhân.
9. Lặp lại các kiểm thử liên quan sau khi sửa và xác nhận báo cáo không bỏ sót bằng cách exclude sai phạm vi.

### 9.3. Những bằng chứng nên lưu cho một thay đổi AOP

| Bằng chứng | Ý nghĩa khi review |
|---|---|
| Ma trận join point / pointcut | Chứng minh phạm vi mong đợi đã được xác định |
| Test assertions cho advice | Chứng minh hành vi chạy đúng, gồm exception/finally |
| Negative tests | Chứng minh điểm không thuộc phạm vi không bị chặn nhầm |
| Báo cáo line + branch coverage | Cho biết phần mã test thực thi theo tiêu chí đo |
| Advice hit / execution counts | Chỉ rõ mức bao phủ interception trên tập join point |
| Danh sách class đã instrument | Chứng minh pipeline đã xử lý các class cần thiết |
| Runtime/JVM args và phiên bản tool | Giúp tái hiện điều kiện thực thi |
| Smoke test từ artifact cuối | Giảm rủi ro môi trường test khác deployment |
| Stack trace hoặc log có correlation | Giúp truy tìm lỗi ở đúng invocation |

Có thể bổ sung mutation testing để kiểm chứng test thực sự phát hiện sai lệch, chẳng hạn cố tình đảo điều kiện pointcut, bỏ một lần gọi `proceed()` hoặc thay đường xử lý exception trong môi trường test cô lập. Đo được nhiều dòng không đảm bảo assert tốt; một mutation sống sót có thể cho thấy thiếu kiểm tra về advice hoặc outcome. Mutation chỉ có giá trị khi biến thể cố tình sai làm thay đổi ngữ nghĩa cần bảo vệ và test có cơ hội quan sát.

## 10. Những sai lầm phổ biến

1. **“Phương thức đích đã được unit test thì advice đã được test.”** Sai; unit test có thể gọi thẳng target, bỏ qua proxy hoặc weaving thực tế.
2. **“Line coverage 100% nghĩa là mọi join point được chặn.”** Sai; code coverage và join-point coverage có mẫu số khác nhau.
3. **“Mock target luôn tương đương test proxy.”** Sai; phải biết mock đứng trước hay sau ranh giới interception.
4. **“Bật agent coverage không ảnh hưởng gì đến weaving.”** Không thể mặc định; các công cụ có thể cùng biến đổi bytecode, cần xác nhận compatibility.
5. **“Break point không dừng là pointcut sai.”** Có thể do khác class, debug symbols, class loader, advice short-circuit hoặc đường gọi vòng qua proxy.
6. **“Mọi lời gọi nội bộ đều không bị chặn.”** Đây là đặc tính của một số proxy, không phải quy luật áp dụng cho mọi kiểu AOP.
7. **“Test chỉ đường thành công là đủ.”** Sai; after throwing, finally, retry, cleanup và exception wrapping có thể bị lỗi riêng.
8. **“Số advice hit bằng số lần target chạy.”** Không đảm bảo khi có short-circuit, retry, nested advice hoặc nhiều tầng interception.
9. **“Report coverage khác nhau thì test code đã sai.”** Cần loại trừ thay đổi bytecode, agent, build artifact và report filters trước.
10. **“Đổi thứ tự advice chỉ ảnh hưởng log.”** Sai; có thể làm thay đổi kiểm tra quyền, kết quả, ngoại lệ và dọn tài nguyên.
11. **“Gỡ lỗi có thể luôn xem stack trace như Java không instrument.”** Lớp dispatch, proxy generated, reflection và mã weave có thể thêm hoặc thay frame.
12. **“Unit test pass trên một JDK bảo đảm runtime mọi môi trường giống nhau.”** Class loader, module access, version, agent và trình tự biến đổi có thể khác.
13. **“Loại bỏ mã AOP khỏi coverage để báo cáo đẹp hơn.”** Nếu vô tình exclude logic advice có yêu cầu chức năng, metric không còn phản ánh rủi ro.
14. **“Thêm logging dày đặc vào mọi advice là cách debug miễn phí.”** Nó có thể đổi timing, che race, tăng I/O và giữ dữ liệu nhạy cảm.

## 11. Bài tập thực hành

### Bài tập 1: Chứng minh phạm vi pointcut

Xây hai phương thức `read()` và `write()` trên cùng một interface. Dùng proxy chỉ ghi nhận `write()`. Viết ba test: gọi `read()` không có advice; gọi `write()` có đúng một advice; gọi `write()` với input lỗi kiểm chứng exception và số lần advice. Sau đó thử pointcut rộng chọn cả hai method để xem negative test có bắt lỗi không.

### Bài tập 2: So sánh report coverage với advice-hit matrix

Với một phương thức được unit test trực tiếp và integration test qua proxy, ghi hai kết quả: phương thức đã được code coverage đánh dấu là chạy hay chưa; advice-hit counter có tăng hay chưa. Giải thích vì sao một phép đo có thể dương trong khi phép đo kia bằng 0.

### Bài tập 3: Điều tra tương tác instrument

Trong một dự án thử nghiệm hỗ trợ weaving và coverage agent, chạy bốn cấu hình baseline, AOP only, coverage only và combined. Lưu classpath, JVM args, version và report. Nếu có chênh lệch, kiểm tra tài liệu về thứ tự biến đổi, thử cấu hình được hỗ trợ và đối chiếu class thật trước khi kết luận.

### Bài tập 4: Tái hiện lỗi do đường gọi nội bộ

Chạy `AopCompatibilityDemo` và xác nhận `valueVisits` vẫn bằng 0 ngay sau `proxy.add(7)`. Sau đó sửa target để một caller bên ngoài gọi `proxy.value()`, quan sát lượt mới. Từ đó giải thích: chính sách AOP của ứng dụng cần bắt **method execution** hay chỉ các lời gọi đi qua proxy?

## 12. Câu hỏi tự kiểm tra

**1. Điều gì khác giữa test logic advice và test tích hợp AOP?** Test logic advice kiểm chứng thân xử lý trong một harness hoặc context giả lập; test tích hợp chứng minh hệ thống thật chọn join point và kích hoạt advice theo đường instrument cần dùng.

**2. Tại sao line coverage có thể 100% khi audit bị bỏ sót?** Vì target vẫn chạy đầy đủ từ đường không qua AOP; bộ đếm dòng không tự xác minh mọi yêu cầu audit tại join point.

**3. Khi nào hai agent Java có thể gây vấn đề?** Khi chúng cùng biến đổi bytecode theo thứ tự hoặc chế độ mà công cụ không hỗ trợ; cần kiểm tra tài liệu, cấu hình, class thực và tổ hợp riêng rẽ.

**4. Vì sao test chỉ assert số lần advice chạy chưa đủ?** Advice vẫn có thể ghi sai dữ liệu, sai thứ tự, nuốt exception hoặc thay đổi kết quả. Cần assert cả hành vi quan sát được.

**5. Self-invocation có luôn bỏ qua advice không?** Không. Nó có thể đi vòng proxy trong một số triển khai; weaving có thể can thiệp ở điểm thực thi tùy khả năng cơ chế.

**6. Muốn debug advice không chạy thì kiểm tra gì đầu tiên?** Xác nhận đang gọi object thật sự được instrument, pointcut đúng, class được nạp đúng phiên bản và không có advice khác chặn đường tiếp tục.

**7. Tại sao cần test artifact cuối?** Vì class đã weave, agent/JVM flags, class loader và runtime dependencies ở bản triển khai có thể khác môi trường unit test.

**8. Có nên exclude toàn bộ code sinh hoặc advice khỏi báo cáo coverage?** Chỉ nên áp dụng filter có lý do và đủ hẹp; vẫn phải giữ phép kiểm chứng bắt buộc của advice và tập join point ở các test riêng.

**9. Làm sao phân biệt target ném lỗi với handler tạo lỗi?** Kiểm tra exception type, cause chain, stack frames và breakpoint ở điểm gọi `proceed()` hoặc `Method.invoke`; mở wrapper đúng cách khi hợp đồng yêu cầu.

**10. Đo bao phủ AOP thế nào để tránh “chỉ số đẹp” giả?** Bắt đầu từ tập join point cần chặn và tập không được chặn do yêu cầu xác định; thử thành công/lỗi, internal/external calls, kiểm chứng hit, outcome và sự kiện thực tế thay vì chỉ cộng số dòng hoặc tỷ lệ.

**Ghi nhớ:** Việc tương thích AOP với kiểm thử, coverage và debugger là một vấn đề **hành vi và bằng chứng thực thi**. Phải kiểm tra đường can thiệp thực, test cả điểm khớp và không khớp, tách code coverage khỏi join-point/advice coverage, xác minh cách bytecode được instrument và sử dụng kết quả debug đúng với artifact runtime.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-integration-deployment-008">Tạo bản dựng lặp lại và phát hiện thay đổi kết quả weaving</a>

<details>
<summary>Xem chi tiết</summary>

# Tạo bản dựng lặp lại và phát hiện thay đổi kết quả weaving trong AOP

## 1. Bài toán: cùng mã nguồn, weaving có còn giống nhau?

Trong lập trình hướng khía cạnh (**Aspect-Oriented Programming – AOP**), *weaving* là quá trình tích hợp các **aspect** vào chương trình tại những **join point** được pointcut lựa chọn. Kết quả có thể là bytecode đã biến đổi, cấu trúc điều phối advice hoặc hành vi can thiệp tại thời điểm nạp lớp/chạy, tùy công cụ. Khi dự án phát triển, chỉ một thay đổi nhỏ ở pointcut, classpath hay phiên bản trình dệt có thể khiến advice áp dụng vào nhiều hoặc ít join point hơn trước.

Một **bản dựng lặp lại (reproducible build)** là bản dựng được tạo từ **cùng bộ đầu vào đã xác định** theo cùng quy trình và tạo ra **kết quả xác định** theo tiêu chí kiểm tra đã chọn. Với AOP cần xem ít nhất hai phương diện:

- **Kết quả nhị phân:** tập file `.class` và nội dung bytecode sau dệt có giống nhau từng byte không? Nếu đóng JAR, bản JAR có giống hệt từng byte không?
- **Kết quả weaving theo ngữ nghĩa:** cùng join point nào được áp dụng advice, thứ tự/precedence có đúng, các nhánh `before`, `around`, `after` và điều kiện động có giữ hành vi mong muốn không?

Hai tiêu chí khác nhau: **hash khác không nhất thiết nghĩa là chức năng đổi** (ví dụ thông tin debug hoặc metadata khác); ngược lại, **hash giống nhau không chứng minh mọi hành vi runtime giống nhau** nếu phiên bản runtime, agent, class loader hoặc cấu hình môi trường khác. Để phát hiện weaving drift đáng tin cậy, cần kết hợp kiểm tra artifact, danh sách join point được dệt và kiểm thử hành vi.

Bài tập trung vào AOP tổng quát, sử dụng **AspectJ** làm ví dụ một bộ công cụ dệt mã cụ thể. Các nguyên tắc khóa đầu vào, xây dựng sạch, fingerprint, baseline và CI có thể áp dụng cho những weaver khác sau khi điều chỉnh cách lấy thông tin.

## 2. Những thay đổi cần phát hiện

Một bộ phát hiện thay đổi weaving nên trả lời được các câu hỏi:

1. **Aspect nào đã tham gia?** Có aspect mới, bị loại khỏi build, bị thay đổi thứ tự áp dụng hoặc precedence không?
2. **Pointcut chọn những join point nào?** Một phương thức mới có vô tình khớp biểu thức wildcard rộng? Có lời gọi nằm ngoài phạm vi weaving?
3. **Bytecode có bị dệt khác?** Các class, method, synthetic helper, control flow và thông tin annotation/debug có thay đổi không?
4. **Hành vi có thay đổi?** Advice chạy ít/nhiều lần, trước/sau, `proceed` bị bỏ qua/lặp lại, exception hoặc dữ liệu đầu ra khác?
5. **Thay đổi có chủ ý không?** Mỗi khác biệt phải truy được về source commit, cấu hình, dependency, toolchain, phạm vi áp dụng hoặc một drift chưa được giải thích.

Ví dụ, một pointcut `execution(* demo..*(..))` có thể chọn thêm phương thức chỉ vì có class mới trong package `demo`. Nếu không kiểm tra các join point, lần build sau có thể thêm logging hoặc transaction boundary ngoài dự kiến dù file `.aj` không đổi. Trường hợp này là **weaving drift do thay đổi mã đầu vào**, không nhất thiết lỗi ở weaver.

## 3. Phân tầng tính lặp lại: từ byte đến ngữ nghĩa

| Tầng kiểm tra | Đầu ra dùng so sánh | Phát hiện tốt | Giới hạn |
| --- | --- | --- | --- |
| Nguồn và cấu hình | Commit, version lock, hash dependencies, flags, config | Đầu vào thay đổi ngoài ý muốn | Đầu vào giống chưa bảo đảm artifact giống |
| Tập file sau dệt | Danh sách đường dẫn tương đối các `.class` | Class mới, mất class, class phát sinh | Chưa cho biết nội dung bytecode |
| Hash của từng `.class` | SHA-256 theo đường dẫn ổn định | Thay đổi byte-for-byte sau dệt | Nhạy với debug info, compiler metadata |
| Artifact đóng gói | Hash JAR/WAR cùng metadata đóng gói | Thay đổi bytes của bản phát hành | Timestamp/thứ tự entry có thể tạo nhiễu |
| Weaving inventory | Tập sự kiện “aspect/advice → join point shadow” | Join point được chọn thêm, mất, thay đổi target | Cần chuẩn hóa log và xác minh log đầy đủ |
| Cấu trúc bytecode | `javap -c -p -l`, classfile parser/diff | Biến đổi control flow và call vào advice | Disassembly cũng có metadata/nhiễu biểu diễn |
| Kiểm thử hành vi | Output, thứ tự advice, count, exception, `proceed` | Sai khác quan sát được trong ca kiểm thử | Không chứng minh đầy đủ mọi đường chạy |

**Tiêu chuẩn nghiêm ngặt nhất** là cùng đầu vào và môi trường được khóa, build hai lần trong môi trường sạch cho **tập class và bytes giống nhau**, rồi xác nhận hành vi dự kiến. Trong thực tế, cũng cần khả năng *phân loại* thay đổi nếu việc đóng gói, vị trí source hoặc debug metadata không hoàn toàn quyết định.

### 3.1. “Giống từng byte” khác “giống chức năng”

Hai class có thể cho kết quả runtime giống nhau nhưng hash khác vì line number/local-variable tables, compiler-version metadata, constant pool, thứ tự thành viên hoặc mô tả source. Đừng loại bỏ mọi khác biệt của file `.class` thành “vô hại” khi chưa xác minh tác động.

Hai bản JAR cũng có thể hash khác dù mọi `.class` giống hệt, chỉ vì timestamp/entry order/MANIFEST thay đổi. Vì vậy, khi **JAR hash khác** nên tiếp tục so sánh tập entry và hash từng class; rồi mới xác định sự khác biệt là đóng gói hay weaving.

Ngược lại, **bytecode giống** chỉ xác nhận output compile/weave của lần đo là giống; load-time weaving hoặc cấu hình agent ở runtime vẫn có thể thay đổi kết quả thực thi.

## 4. Khóa toàn bộ đầu vào của quá trình dệt

Tập đầu vào của weaving rộng hơn mã `.aj`:

| Đầu vào | Ví dụ cần khóa/ghi nhận | Nguy cơ khi bỏ sót |
| --- | --- | --- |
| Source Java/aspect | Commit hash, trạng thái working tree, encoding UTF-8 | Mã gốc hoặc pointcut đổi |
| Binary input | Hash thư mục/JAR `-inpath` | Bytecode đầu vào đã thay đổi |
| Aspect binary | Phiên bản/hash `-aspectpath` nếu dùng binary aspects | Cùng Java source nhưng advice khác |
| Toolchain | JDK vendor/version, `ajc` và runtime tương thích, hệ build | Thay đổi compiler, bytecode, JIT/runtime |
| Build options | `-source`/`-target` hoặc `-17`, debug info, lint, weave options | Khác mức bytecode, metadata, thứ tự xử lý |
| Classpath | Chính xác bộ thư viện, phiên bản, thứ tự và dependency resolution | Type/method resolution khác, class trùng |
| Phạm vi weaving | Packages/classes include/exclude, cấu hình aspect, loader | Thêm/mất join point |
| Đóng gói | Resource, MANIFEST, timestamp, entry order, compression | JAR hash không ổn định |
| Runtime weaving | Agent args, `aop.xml` nếu dùng, class loader, thứ tự load | Kết quả dệt phát sinh khi nạp lớp thay đổi |
| Môi trường | OS, locale, timezone, đường dẫn, biến môi trường, filesystem | Non-determinism hoặc path/encoding drift |

**Lock dependency bằng hash/nội dung thực**, không chỉ tên phiên bản. Một repo artifact hoặc image tag có thể bị thay thế nếu không bảo đảm bất biến. Ghi nhận provenance để khi hash khác còn biết đã đổi input nào. Build sạch phải lấy từ cùng commit và cùng dependency cache/nội dung đã kiểm chứng.

Đặc biệt trong AspectJ, trình biên dịch `ajc` yêu cầu đưa vào đầy đủ nguồn hoặc bytecode cần dệt. Không nên mặc định nó tự tìm mọi source bị ảnh hưởng. Tránh đưa **output directory vào `-inpath`** trong lần build sau, vì có thể biến class đã dệt thành input ngoài ý muốn hoặc làm xuất hiện type trùng.

### 4.1. Weaving mode ảnh hưởng nơi đo

- **Compile-time weaving (CTW):** compiler nhận source Java/aspect rồi sinh class đã dệt. So sánh thư mục class output ngay sau build.
- **Post-compile/binary weaving:** weaver nhận class/JAR chưa dệt qua input như `-inpath` và aspect binary/source phù hợp. Cần lưu fingerprint **trước** và **sau** dệt, đồng thời xác minh không vô tình dệt lại class đã được dệt.
- **Load-time weaving (LTW):** class được biến đổi lúc class loader tải vào JVM. Artifact trên đĩa có thể vẫn là bản chưa dệt, vì vậy hash file đầu vào không phản ánh đầy đủ kết quả weaving thực tế. Cần ghi cấu hình agent/loader, sự kiện weaving runtime và các kiểm thử hành vi; nếu cần so bytes, phải có cơ chế thu bytecode sau biến đổi đáng tin theo bộ công cụ đang dùng.

Không gộp CTW và LTW hoặc dệt đi dệt lại cùng lớp nếu thiết kế không yêu cầu. Khả năng *reweavable* của một engine không thay thế việc kiểm soát đầu vào và tránh double-weaving ngoài ý muốn.

## 5. Một ví dụ AspectJ tối thiểu và quan sát được

Sử dụng JDK 17 trở lên, `ajc` hỗ trợ Java 17, thư viện `aspectjrt.jar` phù hợp. Ví dụ cố ý có đúng một method cần advice để dễ kiểm tra danh sách join point.

**Tệp `src/demo/Main.java`:**

```java
package demo;

public class Main {
    public static void main(String[] args) {
        Calculator calculator = new Calculator();
        int result = calculator.add(2, 3);
        System.out.println("result=" + result);
    }
}

class Calculator {
    int add(int a, int b) {
        return a + b;
    }
}
```

**Tệp `src/demo/AuditAspect.aj`:**

```aspectj
package demo;

public aspect AuditAspect {
    before(int a, int b):
        execution(int demo.Calculator.add(int, int))
        && args(a, b) {
        System.out.println("audit add(" + a + "," + b + ")");
    }
}
```

Pointcut `execution` chọn thời điểm thực thi phương thức `Calculator.add`. `args(a,b)` bind hai đối số, giúp kiểm thử rằng advice chạy một lần khi gọi `add(2,3)`. Không có điều kiện động phụ thuộc thời gian hay nguồn dữ liệu ngoài.

**Cấu trúc nguồn:**

```text
project/
├── src/demo/Main.java
├── src/demo/AuditAspect.aj
└── lib/aspectjrt.jar
```

### 5.1. Hai lần build sạch, cấu hình như nhau

Chạy trong thư mục `project` trên Windows PowerShell (dùng hai thư mục output ban đầu trống, ví dụ từ một checkout mới). Cờ `-17` đặt source/target compliance Java 17; `-encoding UTF-8` cố định cách giải mã source; `-showWeaveInfo` hiển thị những vị trí advice được dệt.

```powershell
ajc -version
java -version

ajc -17 -encoding UTF-8 -g:lines,vars,source -showWeaveInfo -classpath "lib/aspectjrt.jar" -d build-A src/demo/Main.java src/demo/AuditAspect.aj
ajc -17 -encoding UTF-8 -g:lines,vars,source -showWeaveInfo -classpath "lib/aspectjrt.jar" -d build-B src/demo/Main.java src/demo/AuditAspect.aj

java -cp "build-A;lib/aspectjrt.jar" demo.Main
java -cp "build-B;lib/aspectjrt.jar" demo.Main
```

Với shell Unix, hai lệnh chạy cần đổi classpath delimiter từ `;` sang `:`. Kiểm soát trước rằng `build-A` và `build-B` **chưa chứa class/resource cũ**; nếu cần tái chạy, dùng hai thư mục output mới hoàn toàn hoặc một workspace tạm đã được xác minh. Không đưa `build-A` hay `build-B` vào `-inpath`.

**Stdout kỳ vọng của mỗi lần chạy `demo.Main`:**

```text
audit add(2,3)
result=5
```

Thông báo của `ajc -showWeaveInfo` có dạng phụ thuộc phiên bản, đường dẫn và cách xuất diagnostic; nó phục vụ kiểm tra vị trí weaving, không nên mặc định là output byte-for-byte ổn định. Cần giữ cả stdout/stderr và exit status của compiler trong hệ CI. Một thông báo warning không nhất thiết làm build thất bại; chính sách CI nên định nghĩa rõ loại warning phải xử lý.

### 5.2. Kiểm chứng nguồn và artifact

Ngay sau mỗi build:

1. Kiểm tra compiler exit status = 0; nếu build lỗi, **không** so sánh output dở dang.
2. Đếm và liệt kê toàn bộ file `.class` bằng đường dẫn **tương đối** so với `build-A`/`build-B`, sắp thứ tự ổn định.
3. Hash SHA-256 từng file `.class`; tên file có ở một phía mà không ở phía kia là khác biệt.
4. So sánh log weaving đã chuẩn hóa về đường dẫn/nội dung; xem advice nào được dệt tại method nào.
5. Chạy test hành vi trên cả hai output và so output/count/exception với oracle đã chấp thuận.

Nếu mọi class có hash giống nhau nhưng JAR khác, phân tích resource, manifest và metadata của archive riêng.

## 6. Công cụ Java 17 so sánh chính xác cây class sau weaving

Đoạn sau là **chương trình Java 17 độc lập**, không phụ thuộc AspectJ, dùng để so sánh toàn bộ tập `.class` theo **đường dẫn tương đối và SHA-256**. Khác với so kích thước file hoặc timestamp, nó phát hiện cả class thêm/mất và khác một byte trong file. Đặt ở **`ClassTreeDiff.java`** trong thư mục `project`.

```java
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

public class ClassTreeDiff {
    private static Map<String, String> inventory(Path root)
            throws Exception {
        Map<String, String> hashes = new TreeMap<>();

        try (Stream<Path> paths = Files.walk(root)) {
            for (Path file : paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".class"))
                    .toList()) {
                String relative = root.relativize(file)
                        .toString().replace('\\', '/');
                byte[] bytes = Files.readAllBytes(file);
                byte[] digest = MessageDigest.getInstance("SHA-256")
                        .digest(bytes);
                hashes.put(relative, HexFormat.of().formatHex(digest));
            }
        }
        return hashes;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: java ClassTreeDiff <left> <right>");
            System.exit(2);
        }

        Map<String, String> left = inventory(Path.of(args[0]));
        Map<String, String> right = inventory(Path.of(args[1]));

        Set<String> paths = new TreeSet<>(left.keySet());
        paths.addAll(right.keySet());

        int changed = 0;
        for (String path : paths) {
            if (!Objects.equals(left.get(path), right.get(path))) {
                String kind = !left.containsKey(path) ? "ADDED"
                        : !right.containsKey(path) ? "REMOVED"
                        : "CHANGED";
                System.out.println(kind + " " + path);
                changed++;
            }
        }

        if (changed > 0) {
            System.out.println("FAIL: differing class files=" + changed);
            System.exit(1);
        }

        System.out.println("PASS: .class trees byte-identical");
    }
}
```

Lệnh chạy:

```powershell
javac ClassTreeDiff.java
java ClassTreeDiff build-A build-B
```

Nếu hai bản chứa tập class giống nhau và bytes từng class giống nhau, stdout chính xác:

```text
PASS: .class trees byte-identical
```

Nếu có khác biệt, chương trình in `ADDED`, `REMOVED` hoặc `CHANGED` kèm đường dẫn class, rồi thông báo tổng số và thoát với **exit code 1**. Nếu gọi thiếu tham số, thoát **2**. Hệ CI cần xử lý exit code khác 0 thành kết quả thất bại.

**Giới hạn kỹ thuật:** chương trình này chỉ kiểm tra file `.class`, chưa so sánh resource, native libraries hoặc JAR packaging; nó không tự nói phần bytecode khác thuộc logic advice, debug attributes hay compiler. Chỉ dùng đường dẫn output **tin cậy**, tương đương nhau và đã được tạo sạch; với artifact quá lớn, có thể chuyển sang đọc file theo luồng để giảm bộ nhớ, nhưng cùng nguyên tắc hash.

## 7. So sánh weaving theo ngữ nghĩa, không dừng ở hash

Để phát hiện một pointcut tự mở rộng phạm vi, cần một **weaving inventory** được sinh từ cùng lần build. Với AspectJ, `-showWeaveInfo` đưa ra thông tin về weaving, nhưng chuỗi log thô có thể khác bởi đường dẫn tuyệt đối, vị trí source, phiên bản compiler hoặc thứ tự thông báo. Quy trình tốt:

1. Thu đủ diagnostics của từng lần `ajc`, bao gồm warnings/errors và weave info.
2. Chuẩn hóa **chỉ những phần không mang ngữ nghĩa** như tiền tố đường dẫn workspace và định dạng đường dẫn. Giữ nguyên tên aspect, advice kind, pointcut/method signature, tên class, vị trí join point và thông báo cảnh báo.
3. Chuyển thành một tập record có cấu trúc nếu parser của phiên bản compiler cho phép. Ví dụ record khái niệm: `aspect=demo.AuditAspect | advice=before | kind=execution | target=demo.Calculator.add(int,int)`.
4. Sort ổn định, giữ số lần xuất hiện nếu nhiều shadow thực sự khác nhau; tránh biến dữ liệu thành set rồi vô tình xóa các bản ghi trùng có ý nghĩa.
5. Lưu manifest đã được review làm **baseline**. Diff các record giữa nhánh hiện tại và baseline: **added**, **removed**, **changed**. Mỗi thay đổi cần lý do rõ ràng.
6. Nếu format log thay đổi khi nâng compiler, nâng phiên bản parser cùng bộ kiểm thử; đừng dùng regex quá rộng làm mất thông tin quan trọng.

**Lưu ý:** record trên là *định dạng inventory đề xuất*, không phải output nguyên văn cố định của `ajc`. Một số weaver không cung cấp danh sách đầy đủ các join point/guard như mong muốn; khi đó phải kết hợp bytecode inspection và test hành vi. `-showWeaveInfo` không phải bằng chứng đủ về điều kiện runtime như `if(...)` hoặc `cflow(...)`.

### 7.1. Baseline cho thay đổi có chủ đích

Ví dụ pull request chỉnh pointcut từ:

```aspectj
execution(int demo.Calculator.add(int, int))
```

sang biểu thức rộng hơn:

```aspectj
execution(int demo.Calculator.*(int, int))
```

Nếu có thêm `subtract(int,int)`, manifest sẽ có thêm target. Đó có thể là chủ ý hoặc bug. CI nên hiển thị diff cùng nội dung PR để người review quyết định. **Không tự động “chấp nhận baseline mới” chỉ để pipeline xanh**; baseline phải được cập nhật cùng thay đổi ngữ nghĩa đã được kiểm tra.

Với các around advice, kiểm thử cần ghi cả số lần `proceed`, việc bỏ qua gốc, kết quả trả về và exception path. Một manifest nói rằng advice *có thể* dệt tại join point không đảm bảo advice luôn chạy nếu pointcut còn điều kiện động.

## 8. Kiểm tra bytecode sau dệt khi phát hiện hash khác

Khi `ClassTreeDiff` báo `CHANGED demo/Calculator.class`:

1. Xác nhận input class/source có giống nhau trước weaving.
2. So sánh phiên bản `ajc`, JDK, compiler options và aspect bytecode.
3. Disassemble hai class để xem thân method và helper khác nhau:

```powershell
javap -classpath build-A -c -p -l demo.Calculator > calculator-A.txt
javap -classpath build-B -c -p -l demo.Calculator > calculator-B.txt
```

4. Xem thay đổi nằm ở lời gọi vào advice, nhánh điều khiển, kiểu/metadata hay chỉ line table và local variables.
5. So inventory weaving và chạy test hành vi tương ứng; nếu quanh `proceed` hoặc precedence thay đổi, thêm ca test không chỉ hash.

`javap -c -p -l` là công cụ chẩn đoán, **không** phải canonicalizer bytecode được bảo đảm. Chuỗi disassembly, constant-pool indices hoặc debug mapping có thể khác. Việc tự loại bỏ tất cả dòng metadata khi diff có thể che khuất một lỗi cần phát hiện. Nếu cần so ở mức classfile chuyên nghiệp, dùng một parser AST bytecode có kiểm soát phiên bản và phân loại vùng khác nhau rõ ràng.

### 8.1. Nên theo dõi gì ngoài class hash?

- Số lượng class đã dệt và số join point shadow được advice.
- Những aspect/advice mới, mất, hoặc thay đổi áp dụng.
- Kích thước class/method tăng mạnh, helper hoặc synthetic members mới.
- Warnings về join point không match, advice không được áp dụng, classpath và type resolution.
- Đường đi của exception, `around`/`proceed` và số lần advice gọi thực tế.
- Class loader, thứ tự nạp và agent setup nếu weaving xảy ra lúc load.

Không quy mọi thay đổi kích thước class thành regression; phải phân tích phần mới sinh có đúng yêu cầu hay không.

## 9. Thiết kế pipeline CI phát hiện weaving drift

Một pipeline có thể gồm sáu chặng:

```text
Checkout commit cố định
          |
Kiểm tra toolchain/dependency lock + hash
          |
Clean build + weave (lưu log)
          |
Tạo class manifest + hash / đóng artifact
          |
So woven targets + chạy test AOP
          |
Diff với baseline đã duyệt và quyết định pass/fail
```

Thực hành đề xuất:

1. **Preflight:** xác nhận commit, JDK, `ajc`, runtime, classpath và cấu hình; fail nếu thiếu dependency hoặc có file trùng type. Đảm bảo output trống.
2. **Rebuild lặp lại:** chạy **hai clean build độc lập** với cùng lock và cùng môi trường; so `.class` hash để phát hiện tính không quyết định. Với yêu cầu rất cao, lặp lại trên runner khác nhưng giữ môi trường được mô tả và phân tích khác biệt OS.
3. **Artifact diff với bản chuẩn:** so tập class, hash, weaving inventory với commit/baseline được review; không nhầm “hai build hiện tại giống nhau” với “không thay đổi so baseline”.
4. **Weaving contract tests:** dùng testcase kiểm tra advice có/không khớp, đúng số lần, thứ tự và dữ liệu; cả `return`, `throw`, điều kiện động true/false, overloaded methods và kế thừa.
5. **Đóng gói:** kiểm tra thành phần phát hành; thiết lập timestamps/thứ tự entry/MANIFEST xác định nếu muốn **reproducible JAR bytes**; so từng class khi JAR hash khác.
6. **Phê duyệt thay đổi:** đính kèm báo cáo added/removed/changed joins, hash khác và lý do; cập nhật baseline chỉ sau khi xem xét đúng ngữ nghĩa.

Một policy minh họa:

| Kiểm tra | Kết quả | Quyết định |
| --- | --- | --- |
| Hai clean builds cùng đầu vào nhưng class hash khác | Build chưa reproducible ở mức bytes | Fail bước lặp lại, tìm nguồn bất định |
| Build mới khác baseline và có join point không mong muốn | Weaving drift nguy hiểm | Fail, sửa pointcut/phạm vi |
| Hash khác nhưng cùng weaving inventory | Cần phân tích bytecode/metadata | Review thay đổi toolchain/debug/logic |
| Manifest thay đổi có chủ ý, test đúng, baseline được duyệt | Thay đổi được kiểm soát | Có thể cập nhật baseline |
| Weaving inventory giống nhưng test thứ tự advice lỗi | Hành vi sai | Fail; inventory chưa bao phủ semantic |
| Tất cả class hashes giống, nhưng LTW agent config khác | Runtime có thể khác | Bắt buộc kiểm tra cấu hình và test LTW |

**Phân biệt hai phép thử quan trọng:** kiểm tra reproducibility so **A với B từ cùng commit**, còn regression detection so **commit mới với baseline đã duyệt**. Một thay đổi pointcut có chủ ý sẽ khiến artifact khác baseline nhưng vẫn có thể build lặp lại ổn định.

## 10. Thử nghiệm biến đổi để chứng minh hệ thống phát hiện tốt

Để xác minh pipeline không chỉ báo PASS do chạy sai lớp hoặc thiếu parser, hãy chủ động tạo các tình huống kiểm thử trên branch độc lập:

**Ca 1 — không đổi input:** hai thư mục output sạch có cùng source/aspect/toolchain. Kỳ vọng cùng stdout và cùng class hash nếu build quyết định.

**Ca 2 — đổi advice body:** thay `System.out.println("audit add(" + a + "," + b + ")");` bằng thông báo khác. Kỳ vọng class hash đổi; test stdout thay; nếu không thay, kiểm tra build đang chạy artifact cũ.

**Ca 3 — thay pointcut không còn match:** đổi `add` thành một chữ ký khác không tồn tại. Kỳ vọng không còn dòng `audit add(2,3)` ở runtime; weaving inventory mất record cũ. Compiler có thể phát ra warning phù hợp tùy điều kiện/phiên bản.

**Ca 4 — mở rộng pointcut:** thêm một method vào loại target hoặc nới wildcard. Kỳ vọng manifest cho thấy join point mới nếu method thật sự nằm trong phạm vi weaving.

**Ca 5 — build input chưa sạch:** lẫn class đã dệt trước vào `-inpath` hoặc output. Kỳ vọng preflight phát hiện input không đúng provenance; không sử dụng drift report từ một build bị nhiễm để cập nhật baseline.

**Ca 6 — thay đổi chỉ đóng gói:** thay timestamp hoặc thứ tự entry JAR nhưng giữ class bytes. Kỳ vọng JAR hash khác, class hashes giống; cần sửa bước archive normalization thay vì sửa aspect.

**Ca 7 — load-time weaving:** giữ source/JAR nguyên nhưng thay tập include/exclude hoặc agent/class loader. Kỳ vọng runtime weaving event/test có thể thay đổi; chỉ so disk class là không đủ.

Các ca test này là cách đánh giá *khả năng phát hiện* của kiểm soát chất lượng, không phải khuyến nghị đưa biến đổi thử nghiệm vào nhánh phát hành.

## 11. Các yếu tố gây nondeterminism thường bị bỏ sót

### 11.1. Thời gian, đường dẫn và môi trường

Timestamp và thứ tự ZIP entry làm JAR hash khác; absolute source path và debug metadata đôi khi gây khác file `.class` khi build ở hai đường dẫn khác nhau. Locale/charset hoặc timezone cũng có thể ảnh hưởng nếu build scripts sinh mã/metadata dựa trên chúng. Cố định locale/encoding cần thiết, đồng thời kiểm tra **thực nghiệm** chứ không mặc định mọi compiler đều nhúng các trường này.

### 11.2. Input order và duplicate classes

Classpath có class trùng tên hoặc dependency giải quyết phiên bản không xác định dễ tạo kết quả khác. Cố định thứ tự classpath và source list, kiểm soát dependency hashes, không dùng wildcard lấy “phiên bản mới nhất” trong build yêu cầu tái lập. Khi có `-aspectpath`, dùng aspect binary đúng phiên bản compiler hỗ trợ và runtime classpath tương ứng.

### 11.3. Incremental build so với clean build

Incremental build có thể tái sử dụng class cũ, bỏ sót thay đổi phụ thuộc hoặc giữ artifact không còn thuộc source. Dùng **clean build** để làm oracle tái lập; sau đó kiểm tra riêng incremental build có tạo artifact tương đương clean build khi cùng commit. Nếu không tương đương, điều tra build cache, dependency graph hoặc tính đúng của invalidation trước khi chấp nhận incremental output.

### 11.4. Advice ordering và điều kiện runtime

Hai bản cùng tập pointcut target vẫn có thể khác precedence hoặc cách bind đối số. Tập target không bao quát hoàn toàn **semantics**. Pointcut `if(...)`, `cflow(...)` và kiểm tra kiểu runtime cần testcase true/false, nested calls, đa luồng nếu cần. Với `around`, xác minh `proceed` có được gọi một hoặc nhiều lần, thay đổi tham số, exception và kết quả.

### 11.5. Output đồng nhất không đủ để khẳng định hệ thống đúng

Nếu cả hai clean builds cùng sai nhưng giống nhau từng byte, reproducibility vẫn PASS. Cần test chức năng, cấu hình, chính sách precedence và baseline join point để bảo đảm **đúng**. Không nhập nhằng “reproducible” với “verified correct”.

## 12. Thu thập provenance và bằng chứng phát hành

Một record build nên có:

```text
build_id: release-2026-example
source_commit: <exact-commit-sha>
java_runtime: <vendor + version>
aspect_compiler: <ajc version>
aspect_runtime: <artifact version + sha256>
compiler_options: <-17, encoding, debug, weave flags>
compile_classpath: <ordered coordinates + content hashes>
binary_inpath: <ordered input paths + hashes, if any>
aspectpath: <ordered artifact hashes, if any>
weaver_config: <hash and content identity>
weaving_mode: <compile-time | binary | load-time>
output_class_manifest: <stable paths + sha256>
weaving_inventory: <normalized record set>
behavior_test_result: <suite, results, environment>
archive_metadata_policy: <timestamp/order/manifest rules>
```

Đây là **mẫu cấu trúc minh họa**, không phải một file tiêu chuẩn được AspectJ sinh tự động. Các giá trị thực phải đến từ build pipeline; dấu ngoặc nhọn chỉ trường cần điền khi triển khai. Không đặt đường dẫn nội bộ bí mật, token registry hoặc thông tin nhạy cảm vào log công khai.

Nếu build artifact được ký hoặc phát hành qua registry, liên kết manifest và kết quả kiểm tra với digest của artifact. Signature/provenance bảo đảm truy vết và toàn vẹn phân phối; nó **không thay thế** việc xác nhận pointcut và advice đúng ý định.

## 13. Những lỗi phổ biến

- **“Cùng commit là đủ để reproducible.”** Chưa đủ: toolchain, dependencies, flags, weaving mode, classpath và môi trường có thể khác.
- **“Hai JAR hash khác nghĩa là aspect đã dệt khác.”** Chưa chắc: có thể chỉ timestamp, entry order hoặc resources.
- **“Class hash giống nghĩa là LTW không thay đổi.”** Sai: dệt lúc nạp lớp và cấu hình runtime có thể khác mà disk bytes vẫn giống.
- **“Log weaving giống nghĩa là runtime luôn giống.”** Không: dynamic pointcut, order/precedence và dữ liệu runtime vẫn cần kiểm thử.
- **“Chỉ so kích thước class là đủ.”** Không: khác bytes cùng kích thước vẫn xảy ra; dùng SHA-256 theo đường dẫn.
- **“Chỉ so output của hai build hiện tại là phát hiện regression.”** Không: cần so với baseline đã được duyệt.
- **“Mỗi thay đổi log có thể chuẩn hóa bỏ đi cho đỡ nhiễu.”** Nguy hiểm: có thể che mất advice target mới/mất hoặc cảnh báo mới.
- **“Incremental build luôn tương đương clean build.”** Cần thử và xác minh cache invalidation.
- **“`-inpath` có thể chứa output đã dệt từ vòng trước cho tiện.”** Dễ gây input trùng/không rõ provenance; tránh nếu không có thiết kế reweaving rõ ràng.
- **“Cứ cập nhật golden file khi CI đỏ.”** Thay đổi ngữ nghĩa phải có review và test trước khi cập nhật baseline.

## 14. Checklist áp dụng cho dự án thực

1. **Khóa đầu vào:** source revision, aspect, JDK, compiler/weaver version, dependencies hashes, options, classpath order, include/exclude.
2. **Chọn weaving mode:** CTW, binary hay LTW; xác định nơi có bytecode sau dệt để so sánh.
3. **Build sạch ít nhất hai lần:** output độc lập, exit code thành công; so class inventory và SHA-256.
4. **Nếu đóng gói:** so archive metadata và thiết lập timestamp/order ổn định; phân biệt JAR drift với class drift.
5. **Thu join point evidence:** `-showWeaveInfo` hoặc công cụ tương đương, chuẩn hóa đúng các trường vô hại rồi diff với golden baseline.
6. **Chạy behavior tests:** advice count, precedence, dynamic true/false, `around`/`proceed`, return/exception, đúng target.
7. **Điều tra khác biệt:** hash → cấu trúc bytecode → woven targets → runtime behavior; xác minh source và tooling trước.
8. **Cổng CI:** fail khi build không lặp lại, target drift ngoài ý muốn hoặc behavior sai; chỉ duyệt baseline mới sau review.
9. **Gắn provenance:** bảo quản manifest, logs, toolchain identity và digest artifact để truy vết.
10. **Định kỳ thử ca biến đổi có chủ đích:** bảo đảm hệ thống có thể thật sự bắt thêm/mất join point.

**Kết luận:** một bản dựng AOP đáng tin cần **đầu vào đã khóa**, **môi trường build sạch**, **bytecode fingerprint**, **bằng chứng join point được dệt**, và **test hành vi**. So sánh byte-for-byte giúp phát hiện tính không quyết định; weaving inventory và test giúp xác định thay đổi về ngữ nghĩa. Chỉ khi tách hai khía cạnh này, ta mới biết một khác biệt là drift đáng lo, thay đổi có chủ ý hay đơn thuần khác biệt đóng gói.

## Tài liệu tham khảo

- Eclipse AspectJ Development Guide — `ajc`, `-inpath`, `-aspectpath`, `-showWeaveInfo`, `-argfile`, `-17`, `-encoding` và `-d`: https://eclipse.dev/aspectj/doc/latest/devguide/ajc.html
- Eclipse AspectJ Programming Guide — pointcut, advice, weaving và join point semantics: https://eclipse.dev/aspectj/doc/latest/progguide/progguide.html
- AspectJ Load-Time Weaving documentation: https://eclipse.dev/aspectj/doc/latest/devguide/ltw.html
- Reproducible Builds — tổng quan nguồn nondeterminism, build path, timestamps và archive normalization: https://reproducible-builds.org/docs/


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-integration-deployment-009">Bật theo giai đoạn, giám sát và chuẩn bị phương án quay lui</a>

<details>
<summary>Xem chi tiết</summary>

# Bật Aspect theo giai đoạn, giám sát và chuẩn bị phương án quay lui trong AOP

## 1. Tại sao AOP cần kế hoạch triển khai riêng?

Aspect-Oriented Programming (AOP) đưa các hành vi cắt ngang vào nhiều điểm của ứng dụng: ghi log, đo thời gian, kiểm soát truy cập, audit, giới hạn tần suất, retry hoặc xử lý lỗi. Một thay đổi rất nhỏ trong **pointcut** có thể tác động đến hàng trăm phương thức, kể cả những nhánh ít được kiểm thử. Với advice kiểu `around`, thay đổi còn có thể quyết định có gọi `proceed()`, gọi bao nhiêu lần hoặc có ném ngoại lệ, nên rủi ro không chỉ là tăng độ trễ mà còn ảnh hưởng trực tiếp kết quả nghiệp vụ.

Vì vậy, một đợt triển khai AOP an toàn cần giải quyết đồng thời ba câu hỏi:

1. **Bật ở đâu, cho ai và theo mức độ nào?** Chọn đơn vị canary, vùng phạm vi pointcut, nhóm máy/phiên bản và mức thực thi advice.
2. **Quan sát điều gì để biết nên tiếp tục hay dừng?** Đo cả hành vi advice và kết quả nghiệp vụ, so sánh với đường cơ sở, có correlation ID và kiểm tra tác động ngoài ý muốn.
3. **Nếu cần quay lui thì tắt bằng cách nào và mất bao lâu?** Việc tắt một nhánh advice ở runtime khác với loại bỏ proxy, hoàn nguyên bytecode đã dệt hoặc xử lý lớp đã được JVM nạp.

**Nguyên tắc:** chỉ tăng phạm vi khi đã có bằng chứng từ giai đoạn trước và phương án quay lui đã được kiểm chứng; không xem “ứng dụng vẫn khởi động” hoặc “không xuất hiện exception trong log” là đủ để xác nhận AOP an toàn.

## 2. Hiểu đúng những thứ có thể bật hoặc tắt

Trong AOP, **aspect** chứa các mối quan tâm cắt ngang; **pointcut** mô tả tập join point; **advice** thực thi tại các join point phù hợp. Tuy nhiên, “bật aspect” có thể mang nhiều nghĩa khác nhau:

| Cấp điều khiển | Có thể thay đổi | Điều chưa chắc được thay đổi |
| --- | --- | --- |
| Feature flag ngay trong advice | Bỏ qua hoặc áp dụng **logic do advice kiểm soát** ở lần gọi sau | Không tháo bytecode đã weave; không xóa overhead vào advice; không đảm bảo vô hiệu hóa hành vi khác |
| Predicate/pointcut động nếu engine hỗ trợ | Bật/tắt match theo ngữ cảnh, đối tượng hoặc đối số | Có thể vẫn có chi phí matcher/interceptor; không bảo đảm mọi join point có thể đổi lúc chạy |
| Cấu hình lắp chuỗi interceptor/proxy | Có thể thay interceptor cho các lời gọi mới theo vòng đời cấu hình | Các tham chiếu proxy/target đã phát tán hoặc tác vụ đang chạy có thể giữ trạng thái cũ |
| Compile-time weaving (CTW) | Phát hành artifact chứa class đã dệt hoặc artifact gốc | Một flag runtime không tự loại bỏ mã đã dệt |
| Post-compile/binary weaving (PCW) | Triển khai lại artifact trước/sau khi weave | Các instance cũ vẫn chạy bytecode cũ đến khi thay thế |
| Load-time weaving (LTW) | Điều chỉnh agent, cấu hình weave và khởi động tiến trình mới | Lớp **đã được định nghĩa** không tự “unweave” khi tắt cấu hình agent; phụ thuộc công nghệ nạp lại class |
| Proxy/interception AOP | Điều chỉnh đường gọi qua proxy hoặc chế độ interceptor | Lời gọi bỏ qua proxy vẫn không được advice xử lý; thay đổi reference không tự lan đến mọi caller |

Từ đó phải có **hai bản đồ** riêng: (a) nhóm phương thức sẽ chịu tác động của pointcut; (b) nhóm máy, tiến trình, classloader và artifact thực sự đang mang cơ chế AOP ấy. Một flag tắt logic advice chỉ tác động đúng nơi nó được kiểm tra; không phải nút hoàn nguyên chung cho tất cả công nghệ AOP.

## 3. Phân loại mức độ rủi ro trước khi rollout

Không nên triển khai mọi loại advice theo cùng chiến thuật.

| Loại advice | Tác động thường gặp | Điều kiện triển khai cần quan tâm |
| --- | --- | --- |
| Tracing và metrics chỉ quan sát | CPU, memory allocation, cardinality, I/O của exporter | Tỷ lệ sampling, ngân sách overhead, mất dữ liệu có chấp nhận được không |
| Audit/ghi log nhạy cảm | I/O, dữ liệu cá nhân, độ trễ, nhân đôi bản ghi | Lọc dữ liệu, retry/idempotency, khả năng audit bắt buộc |
| Cache/circuit breaker/rate limiter | Thay đổi đường trả kết quả, timeout, từ chối request | Hành vi khi dependency lỗi, giới hạn phạm vi, fallback |
| Authorization/security | Cho phép hoặc chặn hành vi nghiệp vụ | Chính sách **fail-open hay fail-closed** phải được phê duyệt từ yêu cầu bảo mật |
| Retry/transaction advice | Gọi `proceed()` nhiều lần, thay đổi số lần side effect hoặc ranh giới giao dịch | Idempotency key, khả năng khôi phục dữ liệu và xử lý partial completion |

Đối với cơ chế quan sát thuần túy, có thể triển khai **shadow/observe-only**: vẫn tính predicate và ghi đo lường ở mức tối thiểu nhưng không thay đổi kết quả nghiệp vụ. Đối với kiểm soát quyền, shadow chỉ giúp so sánh chính sách mới và cũ; **không dùng nó làm biện pháp thay thế việc bắt buộc kiểm tra quyền**. Không được tắt logic bảo vệ có yêu cầu fail-closed chỉ để giảm báo lỗi.

Phải ghi rõ **bất biến (invariant)** trước khi rollout, ví dụ: một giao dịch thành công chỉ được tạo một bản ghi thanh toán; advice không được nuốt exception nghiệp vụ; audit không lưu credential; việc bật/tắt không làm sai thứ tự các advice bắt buộc.

## 4. Các giai đoạn triển khai đề xuất

Một tiến trình thực tế có thể dùng các trạng thái sau (tên trạng thái là thiết kế minh họa, không phải API chuẩn của AOP):

```text
DESIGNED
   |
   v
OFF / instrument-ready
   |
   v
SHADOW (đọc, quan sát, không đổi quyết định nghiệp vụ)
   |
   v
CANARY (một nhóm nhỏ, có tiêu chí dừng)
   |
   v
EXPAND (mở rộng từng nấc theo metric)
   |
   v
FULL (mọi đối tượng trong phạm vi đã phê duyệt)
   |
   +------> HOLD (dừng mở rộng, điều tra)
   |
   +------> ROLLBACK (quay về trạng thái an toàn đã biết)
```

### 4.1. OFF — đặt nền giám sát và xác định phạm vi

Trước khi bật logic mới:

- Khoanh **pointcut** theo lớp/gói/method cần thiết. Kiểm tra cả overload, implementation, annotation, interface, kiểu join point (`call`, `execution`, v.v.) và lời gọi nội bộ nếu cơ chế dệt hỗ trợ.
- Xác nhận số join point có thể bị ảnh hưởng từ báo cáo weaving, danh sách matched methods hoặc phép thử instrumentation. Không để một biểu thức wildcard vô tình bắt logging helper, serializer hoặc chính advice.
- Tạo số liệu baseline: p50/p95/p99 latency, error/timeout rate, request throughput, CPU, heap/GC, queue depth, số lần gọi nghiệp vụ, tỉ lệ side effect không mong muốn.
- Xác nhận có đường **rollback riêng** cho artifact, config, agent và data. Lưu version/hash của artifact hiện tại và câu lệnh triển khai ngược.
- Chuẩn bị quyền vận hành, dashboard, cảnh báo, trách nhiệm quyết định mở rộng/dừng và thời gian theo dõi.

Với CTW/PCW, ngay cả khi một flag đặt OFF, bytecode đã dệt vẫn khác bản trước. Cần đo baseline riêng giữa **artifact gốc** và **artifact woven ở chế độ OFF**, vì overhead hoặc lỗi weaving có thể tồn tại khi nhánh advice chính chưa bật.

### 4.2. SHADOW — quan sát nhưng không thay đổi kết quả

Trong shadow mode, advice có thể đánh giá điều kiện hoặc tính toán quyết định dự kiến mà không chặn lời gọi, không retry, không sửa tham số/return value, không tạo side effect nghiệp vụ và không thay thế exception. Ví dụ một policy guard có thể ghi số sự kiện “would deny”, nhưng vẫn để logic hiện hữu quyết định.

Kiểm tra trong shadow:

- Số lần match theo join point kind và chữ ký; so sánh với số invocation nghiệp vụ.
- Chênh lệch kết quả dự kiến giữa chính sách mới và hành vi thực tế; lưu **thống kê tổng hợp**, tránh dữ liệu nhạy cảm.
- Overhead do match, capture arguments, instrumentation, log và exporter gây ra.
- Có phát sinh recursion, advice lặp, thay đổi thứ tự hoặc context propagation lỗi không.
- Exception path có giữ nguyên stack, type và thứ tự cleanup như trước không.

Shadow **vẫn có thể gây hại** nếu advice tự gửi network request, tạo object lớn, gọi `toString()` trên tham số có side effect, hoặc gây quá tải hệ thống log. “Không sửa business result” không có nghĩa “không có rủi ro”.

### 4.3. CANARY — kích hoạt trên nhóm xác định

Chọn một phân vùng ổn định, chẳng hạn nhóm máy/instance, tenant thử nghiệm hoặc tập request được ánh xạ nhất quán. Nếu AOP được weave vào artifact, phát hành **canary artifact** cho một số instance; nếu advice hỗ trợ feature flag runtime, có thể bật logic cho nhóm được phê duyệt.

Các lưu ý:

- Cùng một tác vụ liên quan nhiều request hoặc nhiều service nên có **quy tắc phân nhóm nhất quán**; nếu bật ngẫu nhiên lại ở từng join point, workflow có thể nửa bật nửa tắt.
- Căn cứ tỷ lệ trên **đơn vị nghiệp vụ phù hợp** (ví dụ tenant hoặc session), không chỉ số lượng request.
- Nếu nhóm canary khác về tải, tính năng hoặc vùng mạng so với control group, không suy ra chênh lệch do aspect mà chưa kiểm soát khác biệt đó.
- Khởi động ở phạm vi có rủi ro được chấp nhận và mở rộng theo kế hoạch phê duyệt. Các tỷ lệ như 1% → 5% → 25% → 100% chỉ là **ví dụ**, không phải ngưỡng mặc định an toàn cho mọi hệ thống.

### 4.4. EXPAND — tăng dần sau khi vượt tiêu chí

Tại mỗi nấc, đợi đủ lưu lượng và khoảng quan sát để số liệu có ý nghĩa; không nhất thiết dùng một thời lượng cố định. So với baseline và control group trên cùng khoảng thời gian, xét cả giai đoạn cao tải, lỗi dependency và đường exception quan trọng.

Các điều kiện “go/no-go” cần được định nghĩa **trước** rollout: ngưỡng tăng latency, error, side effect, tài nguyên, queue depth, số lần advice/proceed và cảnh báo bảo mật. Nếu thiếu dữ liệu hoặc số mẫu chưa đủ, giữ nguyên nấc (**HOLD**) thay vì coi “chưa có lỗi” là bằng chứng thành công.

### 4.5. FULL — hoàn thành nhưng vẫn có cơ chế dừng

Khi rollout full, ghi lại phạm vi pointcut, version của advice, artifact hash, classloader/agent config, trạng thái flag và ngày giờ chuyển trạng thái. Duy trì giám sát sau triển khai để phát hiện trường hợp lưu lượng thấp, theo chu kỳ hoặc theo lô không xuất hiện trong canary.

## 5. Thiết kế feature flag đúng với AOP

Một advice có thể sử dụng **một snapshot cấu hình bất biến cho mỗi invocation**:

```text
join point entered
  -> capture rollout version/mode một lần
  -> determine target cohort
  -> OFF? proceed bình thường
  -> SHADOW? observe rồi proceed
  -> ON? áp dụng advice theo hợp đồng rồi proceed/return/throw
```

Các quy tắc quan trọng:

1. **Tính nhất quán theo invocation:** nếu mode thay đổi ngay giữa một `around`, nên dùng snapshot đã đọc lúc vào để việc cleanup và xử lý kết quả không bị nửa logic cũ, nửa logic mới.
2. **Tính nhất quán theo workflow:** một thao tác nhiều bước có thể cần cohort quyết định từ đầu và được truyền qua các lời gọi sau, không tính ngẫu nhiên lại.
3. **Chính sách lỗi cấu hình:** nếu hệ thống flags không truy cập được, fallback phải dựa trên yêu cầu nghiệp vụ/bảo mật. Quan sát metrics có thể chấp nhận OFF; authorization bắt buộc thường phải giữ chính sách bảo vệ, không tự động bỏ qua.
4. **Tránh gọi dịch vụ cấu hình trên từng join point:** dùng cache/snapshot theo phiên bản và cơ chế cập nhật không gây vòng advice trên đường truy xuất cấu hình.
5. **Giảm side effect trong SHADOW:** không nhân bản ghi dữ liệu, giao dịch, email, request thanh toán hay lệnh `proceed()`; so sánh quyết định bằng hàm thuần hoặc bản sao trạng thái an toàn.
6. **Tách flag của chức năng với kill switch:** flag rollout tăng/giảm cohort; kill switch nhằm vô hiệu hóa nhanh hành vi không bắt buộc khi vượt ngưỡng, với người chịu trách nhiệm và quyền truy cập rõ ràng.

Bật/tắt flag từ xa chỉ có ý nghĩa nếu **mọi tiến trình liên quan đều thực sự đọc được thay đổi**. Cần đo thời gian truyền cấu hình, trạng thái hiện tại của từng instance, khả năng tiến trình mất kết nối tiếp tục dùng bản cũ, và cách xử lý invocation đang chạy khi flag chuyển OFF.

## 6. Ví dụ Java 17 chạy độc lập: OFF → SHADOW → ON → ROLLBACK

Ví dụ sau dùng **JDK dynamic proxy** để minh họa một interceptor đóng vai trò advice có chế độ runtime. Đây là mô hình AOP qua delegation proxy, **không phải chương trình weaving** và không yêu cầu thư viện ngoài JDK. Tất cả trạng thái lưu trong một tiến trình để output có thể kiểm chứng.

Tạo file **`AopRolloutDemo.java`**:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

public final class AopRolloutDemo {
    enum Mode { OFF, SHADOW, ON }

    interface PaymentService {
        void pay(String reference);
        void health();
    }

    static final class PaymentServiceImpl implements PaymentService {
        @Override
        public void pay(String reference) {
            System.out.println("target pay " + reference);
        }

        @Override
        public void health() {
            System.out.println("target health");
        }
    }

    private static final AtomicReference<Mode> MODE =
            new AtomicReference<>(Mode.OFF);

    private static Object invokeTarget(
            PaymentService target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    public static void main(String[] args) {
        PaymentService target = new PaymentServiceImpl();
        PaymentService proxy = (PaymentService) Proxy.newProxyInstance(
            PaymentService.class.getClassLoader(),
            new Class<?>[] { PaymentService.class },
            (ignoredProxy, method, methodArgs) -> {
                // Pointcut minh họa: chỉ các lời gọi pay đi qua proxy.
                if (!method.getName().equals("pay")) {
                    return invokeTarget(target, method, methodArgs);
                }

                // Một snapshot trên mỗi invocation.
                Mode snapshot = MODE.get();
                if (snapshot != Mode.OFF) {
                    System.out.println(
                        "observe mode=" + snapshot + " ref=" + methodArgs[0]);
                }
                if (snapshot == Mode.ON) {
                    System.out.println("advice active ref=" + methodArgs[0]);
                }
                return invokeTarget(target, method, methodArgs);
            }
        );

        System.out.println("phase OFF");
        proxy.pay("P1");

        MODE.set(Mode.SHADOW);
        System.out.println("phase SHADOW");
        proxy.pay("P2");

        MODE.set(Mode.ON);
        System.out.println("phase ON");
        proxy.pay("P3");

        MODE.set(Mode.OFF); // Kill switch minh họa.
        System.out.println("phase ROLLBACK");
        proxy.pay("P4");
        proxy.health();
    }
}
```

Biên dịch/chạy:

```bash
javac AopRolloutDemo.java
java AopRolloutDemo
```

**Standard output chính xác:**

```text
phase OFF
target pay P1
phase SHADOW
observe mode=SHADOW ref=P2
target pay P2
phase ON
observe mode=ON ref=P3
advice active ref=P3
target pay P3
phase ROLLBACK
target pay P4
target health
```

Diễn giải:

- Với **OFF**, gọi `pay` vẫn đi qua proxy/interceptor nhưng không in phần quan sát/advice; target luôn được gọi đúng một lần.
- Với **SHADOW**, có marker quan sát và target chạy bình thường; không thay đổi hành vi nghiệp vụ.
- Với **ON**, chạy thêm phần advice minh họa rồi gọi target đúng một lần.
- Sau khi chuyển về **OFF**, lời gọi tiếp theo không còn chạy phần logic advice; proxy vẫn tồn tại.
- Method `health()` không khớp pointcut minh họa và được ủy quyền thẳng tới target.

Đây là **ví dụ kiểm thử điều khiển**, không đại diện cho một hệ thống config phân tán hay code bảo mật production. Trong thực tế, không đưa ID nhạy cảm vào log; dùng histogram/counter với nhãn cardinality thấp và correlation ID bảo vệ phù hợp. `AtomicReference` ở đây chỉ quản lý snapshot của **một JVM**. Nó không tự đồng bộ cờ giữa instance, không loại bỏ weaving và không chặn các caller đang giữ tham chiếu target gốc.

Một phiên bản production của `around` còn phải giữ nguyên exception, return value và số lần `proceed()` theo hợp đồng, đồng thời đặt ghi nhận thời gian/cleanup trong `finally` khi cần. Tắt một flag không cho phép bỏ qua advice bảo vệ bắt buộc.

## 7. Giám sát: quan sát cả advice lẫn kết quả nghiệp vụ

Chỉ theo dõi số log của advice là không đủ. Ít nhất cần bốn lớp đo lường:

| Lớp | Metric/sự kiện | Câu hỏi trả lời |
| --- | --- | --- |
| **Rollout/config** | `aspect_version`, `rollout_mode`, config revision, số instance theo cohort | Có bao nhiêu process thực sự nhận cấu hình mới? |
| **AOP engine** | Số join point match, advice enter/exit, exceptions, `proceed()` count, depth | Advice được chọn đúng nơi, đúng lần và đúng thứ tự chưa? |
| **Hiệu năng** | p50/p95/p99 latency, CPU, allocations, GC, queue, I/O, exporter backlog | Weaving và advice làm hệ thống chậm hoặc quá tải không? |
| **Nghiệp vụ và an toàn** | Tỉ lệ lỗi, timeout, từ chối hợp lệ, kết quả giao dịch, duplicate side effects | Có thay đổi quyết định hoặc tính đúng đắn không? |

Dữ liệu nên chia theo **phiên bản artifact**, **cohort**, **vùng triển khai**, **aspect/pointcut ID**, **nhóm join point**, **mode**, nhưng hạn chế gắn nhãn số lượng lớn như user ID, email, payment ID hoặc stack trace vào metric. Những thông tin chi tiết có thể nằm trong trace/log có phân quyền và retention thích hợp.

**Đừng lấy số lần advice làm mẫu số cố định bằng request count.** Một request có thể đi qua nhiều join point hoặc nhiều attempt; một `around` có thể gọi `proceed()` nhiều lần; async callback có thể thuộc invocation khác. Nên định nghĩa tỷ lệ riêng:

- `advice_invocations / eligible_join_point_invocations` để ước lượng coverage.
- `business_errors / business_attempts` để theo dõi tỷ lệ lỗi nghiệp vụ.
- `duplicated_side_effects / confirmed_business_operations` khi có dữ liệu xác nhận.
- Overhead tương đối từ canary so với control group, với workload tương đồng.
- `config_applied_instances / expected_instances` để theo dõi mức lan truyền flag.

Các phép đo ở trên là **công thức ý nghĩa**, không phải quy tắc xác định mẫu số cho mọi hệ thống. Khi lọc nhóm canary, phải dùng cùng cửa sổ thời gian, đơn vị đo và các điều kiện loại trừ trên hai nhóm so sánh.

### 7.1. Trace mẫu

```text
seq=01 req=R8 inv=I1 app=v17 cohort=canary mode=SHADOW advice=AuditCheck phase=enter
seq=02 req=R8 inv=I1 app=v17 cohort=canary mode=SHADOW advice=AuditCheck phase=decision decision=would-allow
seq=03 req=R8 inv=I1 app=v17 cohort=canary mode=SHADOW advice=AuditCheck phase=exit outcome=success
```

Đây là **định dạng log minh họa**, không phải output của chương trình Java. Nó cho phép so sánh cùng một `invocationId` qua nhiều pha và xác định mode tại thời điểm bắt đầu. Trong log thật, không ghi dữ liệu người dùng nhạy cảm ở trường `decision` hay `args`; gộp theo loại quyết định, lưu tham chiếu truy vết với cơ chế bảo vệ phù hợp.

### 7.2. Cảnh báo và tiêu chí dừng

Định nghĩa sẵn **ngưỡng cảnh báo và điều kiện rollback** từ mức chịu đựng lỗi và baseline cụ thể, chẳng hạn:

- Lỗi/timeout, p99 latency hoặc CPU tăng vượt ngân sách đã phê duyệt trong một khoảng đủ mẫu.
- Phát hiện side effect trùng, mismatch quyền truy cập, vi phạm invariant, advice gọi `proceed()` sai số lần, hoặc vòng recursion.
- Mất sự kiện audit bắt buộc hay xuất hiện dữ liệu nhạy cảm trong log.
- Weaving có dấu hiệu không nhất quán: một số instance thiếu aspect, khác checksum artifact, agent không bật, hoặc config lag lâu hơn giới hạn.
- Không đủ số liệu xác thực canary do telemetry mất dữ liệu.

Với vi phạm bảo mật hoặc dữ liệu, **có thể cần dừng ngay** theo quy tắc nghiêm ngặt hơn ngưỡng thống kê hiệu năng. Quyền gọi kill switch và trách nhiệm xử lý incident phải được xác định trước; không buộc người vận hành tự suy đoán lúc sự cố xảy ra.

## 8. Giám sát và điều khiển trong hệ thống nhiều thread

Với Java concurrent/async code, lời gọi advice trên thread khác có thể được tính vào cùng một nghiệp vụ nhưng xảy ra sau khi mode được chuyển. Vì vậy:

- Truyền `requestId`, `rollout cohort` và cấu hình snapshot qua các ranh giới async khi contract yêu cầu một workflow dùng một chế độ thống nhất.
- Không giả định `ThreadLocal` được tự động sao chép sang thread của executor khác. Nếu dùng `ThreadLocal` để chống reentrancy, dọn trong `finally`.
- Đảm bảo telemetry vẫn ghi khi advice ném exception hoặc code bị hủy sớm.
- Phân biệt thời điểm **cập nhật flag**, **tiếp nhận invocation mới**, và **kết thúc các invocation đang chạy**.
- Đo số workflow đang ở mode cũ trước khi tiến hành bước phụ thuộc vào việc toàn bộ hệ thống đã chuyển sang mode mới.

Ngay cả khi một flag runtime được đồng bộ ngay giữa các JVM, những invocation đã đọc snapshot ON có thể hoàn tất logic advice sau thời điểm operator bấm OFF. Kill switch có thể **ngăn việc bắt đầu tác động mới**, nhưng không tự động đảo ngược side effect đã xảy ra.

## 9. Chiến lược rollback theo cơ chế AOP

### 9.1. Runtime flag hoặc proxy/interceptor

Nếu điểm chặn còn hoạt động và logic advice tự kiểm tra flag:

1. Chuyển mode về cấu hình an toàn đã định trước, thường là OFF cho instrumentation không thiết yếu, hoặc chính sách bảo vệ cũ/được phê duyệt đối với security advice.
2. Kiểm tra số instance nhận được config revision mới; yêu cầu xác nhận từ hệ thống config/telemetry.
3. Theo dõi sự suy giảm của số invocation chạy nhánh ON và chờ các workflow đang chạy hoàn tất nếu business contract đòi hỏi.
4. Nếu sự cố vẫn tiếp diễn do overhead proxy hoặc weaving, dùng rollback artifact thay vì lặp đi lặp lại thao tác flag.

**Giới hạn:** OFF trong advice không loại bỏ hoàn toàn interceptor, các điểm đã weave hoặc chi phí đi vào advice; nếu lỗi nằm ở class verification, initialization, classloader hay bytecode dệt, runtime flag có thể không cứu được.

### 9.2. Compile-time và post-compile weaving

Rollback thường là **phát hành lại artifact đã xác thực trước đó**:

```text
known-good artifact A  -- rollout -->  woven artifact B
                                      |
                             incident detected
                                      |
                                      v
                         deploy verified artifact A
                         + restore matching config
                         + verify business invariants
```

Giữ file JAR/class artifact theo hash và dependency lock, không chỉ source tag. Đặc biệt tránh dựng lại “bản cũ” bằng một toolchain khác mà chưa kiểm chứng: bytecode, dependency hoặc kết quả weaving có thể khác. Khi rollback trong hệ thống phân tán, các instance B có thể còn xử lý tác vụ trong lúc A được triển khai lại; contract dữ liệu và giao thức phải tương thích trong cửa sổ chuyển đổi.

Nếu deployment có database migration hay external side effects, rollback code **không tự hoàn tác** thay đổi schema, trạng thái transaction hoặc thông điệp đã gửi. Chuẩn bị migration theo hướng tương thích tiến/lùi và quy trình bù trừ khi nghiệp vụ cho phép; không suy luận rollback bytecode đồng nghĩa rollback dữ liệu.

### 9.3. Load-time weaving

Với LTW bằng Java agent, rollback có thể đòi hỏi:

- Sửa startup flags để dùng lại bộ agent/aspect/config đã biết tốt hoặc chạy không agent nếu kiến trúc cho phép.
- Khởi động tiến trình mới từ artifact/config đã được xác nhận.
- Drain traffic khỏi các tiến trình cũ và thay thế chúng; **lớp đã load thường không tự mất weaving khi chỉ xóa agent flag ở hệ thống cấu hình**.
- Xác nhận agent, classloader, `META-INF/aop.xml`, include/exclude pattern và class version được áp dụng đúng tại thời điểm load.

Một số nền tảng có khả năng retransformation/redefinition nhưng giới hạn riêng; **không mặc định** có thể unweave mọi lớp đang chạy. Nên thiết kế rollback LTW theo nguyên tắc thay tiến trình/lớp từ trạng thái sạch, trừ khi công cụ có cam kết cụ thể đã được thử nghiệm.

### 9.4. Quay lui theo nhóm máy hoặc phân vùng

Dùng rolling/canary rollback khi rủi ro đã được cô lập; chuyển toàn bộ hệ thống về known-good version khi phát hiện lỗi nghiêm trọng ảnh hưởng dữ liệu hoặc bảo mật. Ưu tiên cách ly các instance lỗi, giữ đủ năng lực tải trên nhóm còn lại, không để canary “đã tắt” nhưng vẫn nhận traffic từ một scheduler hoặc hàng đợi chưa được drain.

## 10. Runbook khi có sự cố AOP

Một runbook tối thiểu nên có các bước tác nghiệp cụ thể và điều kiện ra quyết định.

| Bước | Hành động | Bằng chứng cần lưu |
| --- | --- | --- |
| 1. Nhận diện | Xác nhận symptom, giai đoạn rollout, version, cohort và phạm vi ảnh hưởng | Dashboard snapshot, error examples, timestamp |
| 2. Khoanh vùng | So với control/previous version; đối chiếu pointcut và method chịu tác động | Trace đã ẩn dữ liệu nhạy cảm, match summary |
| 3. Quyết định | HOLD, tắt advice runtime, giảm cohort, hoặc rollback artifact/agent | Tiêu chí dừng đã định, người quyết định |
| 4. Thực thi | Áp dụng kill switch hoặc redeploy known-good; giữ đường config/ops khả dụng | Change ID, checksum, config revision |
| 5. Xác nhận | Kiểm tra instance đã cập nhật, telemetry và invariant nghiệp vụ | Phần trăm rollout thực, kết quả smoke test |
| 6. Khôi phục | Xử lý giao dịch lỗi/duplicate side effect nếu có; theo dõi dài hơn | Danh sách vụ việc, đối soát và tác vụ bù |
| 7. Rút kinh nghiệm | Cập nhật pointcut test, threshold, detection và playbook | Regression test, kế hoạch rollout mới |

Runbook cần trả lời cả câu hỏi **“Ai có quyền rollback?”**, **“Tắt tại đâu?”**, **“Làm sao biết đã tắt ở mọi nơi?”** và **“Các tác vụ đang chạy được xử lý thế nào?”**. Chỉ viết “tắt flag khi lỗi” không đủ khi một số lớp đã được weave, một số instance chưa nhận config hoặc security guard không được phép fail-open.

## 11. Kiểm thử bắt buộc trước khi phát hành

### 11.1. Kiểm thử đúng phạm vi pointcut

- **Positive:** phương thức cần advice nhận đúng advice, đúng số lần, đúng kiểu join point.
- **Negative:** phương thức không thuộc phạm vi không có side effect từ advice.
- **Boundary:** overload, generic bridge method, interface/implementation, self-invocation, method modifiers và classloader khi cơ chế AOP liên quan.
- **Recursion:** helper được advice gọi không tạo vòng lặp advice vô hạn; các trường hợp `proceed()` nhiều lần phải có contract và idempotency rõ.
- **Ordering:** trước/sau với nhiều `around` và `before`/`after` theo precedence cụ thể; kiểm tra cả đường exception.

### 11.2. Kiểm thử chức năng các mode

Test một invocation đại diện trong `OFF`, `SHADOW`, `ON` và sau rollback, so:

- Số lần target được gọi, return value và loại exception.
- Side effect thực tế trong từng mode.
- Việc shadow có giữ nguyên quyết định nghiệp vụ.
- Thời điểm chuyển flag và hành vi của invocation đã bắt đầu.
- Log/metric có đúng version, mode, cohort, advice ID.

Không viết test “OFF không có advice” bằng cách chỉ kiểm tra **không xuất hiện log**; cần quan sát được cả việc đường nghiệp vụ diễn ra đúng và các thao tác có side effect không được thực thi.

### 11.3. Kiểm thử vận hành

- Triển khai canary nhỏ rồi quay lui trên môi trường tương đương production.
- Ngắt tạm dịch vụ cấp flag để kiểm tra fallback đã định.
- Tạo concurrency/async load để kiểm tra context, overhead, queue, latency, CPU và GC.
- Nếu CTW/PCW: xác nhận JAR/bytecode artifact đúng version; test chính artifact sẽ phát hành.
- Nếu LTW: test command line/agent/classloader y như khi deploy; reboot/restart để xác nhận điểm weave xuất hiện đúng.
- Đối với transaction/security advice: mô phỏng exception giữa chuỗi, dependency timeout và partial completion; kiểm tra không làm mất audit/quyền hoặc nhân bản side effect.

## 12. Những sai lầm thường gặp

1. **Bật trên 100% instance ngay sau unit test.** Unit test hiếm khi phản ánh số join point thực tế, contention và chi phí exporter dưới tải.
2. **Cho rằng flag OFF tháo bỏ weaving.** Nó chỉ bỏ hành vi có nhánh được kiểm tra; bytecode và interception boundary vẫn có thể tồn tại.
3. **Tính rollout 5% trên mỗi invocation độc lập.** Một workflow nhiều bước có thể chạy hai mode khác nhau, gây trạng thái không nhất quán.
4. **Chỉ nhìn metric của aspect.** Advice vẫn in log đúng nhưng có thể nhân đôi giao dịch, thay đổi exception hoặc tăng latency nghiệp vụ.
5. **Dùng user ID làm nhãn metric.** Cardinality cao và nguy cơ lộ dữ liệu; phân nhóm theo cohort và type, giữ định danh chi tiết trong trace có bảo vệ.
6. **Dùng shadow nhưng vẫn gọi `proceed()` hai lần để so sánh.** Có thể nhân đôi cập nhật dữ liệu; shadow phải tính toán không tạo side effect nghiệp vụ.
7. **Rollback code mà quên dữ liệu.** Side effect đã thực hiện, tin nhắn đã gửi và migration không tự đảo ngược.
8. **Không kiểm tra lớp đã load trong LTW.** Đổi file cấu hình không có nghĩa class hiện tại được unweave.
9. **Fail-open với authorization để giảm lỗi.** Phải tuân thủ chính sách an toàn đã định; việc rollback không được làm mất kiểm soát bắt buộc.
10. **Không truyền version/mode vào telemetry.** Không thể phân biệt cohort, instance hoặc advice cũ/mới khi xem trace.
11. **Lấy thiếu lỗi trong vài phút làm bằng chứng thành công.** Cần đủ mẫu và bao phủ các nhánh nghiệp vụ có ý nghĩa.
12. **Cho rằng mọi proxy đều giống nhau.** Giới hạn của delegation proxy, subclass interception và bytecode weaving khác nhau, nên thủ tục rollback cũng khác.

## 13. Checklist triển khai và quay lui

### Trước rollout

- [ ] Xác định rõ pointcut, loại join point, advice type và tập class/method dự kiến bị tác động.
- [ ] Phân loại advice là quan sát, thay đổi luồng điều khiển, ghi side effect hay bảo vệ bắt buộc.
- [ ] Khóa artifact version, cấu hình weaving, agent và policy; biết chính xác output production đang chạy.
- [ ] Thiết lập baseline và control group hợp lý, dashboard theo cohort/version, tiêu chí dừng đã thống nhất.
- [ ] Đã thử OFF → SHADOW → ON → OFF và smoke test dương/âm, exception, concurrency nếu có.
- [ ] Đã diễn tập rollback thực tế cho đúng cơ chế: flag, proxy config, CTW/PCW artifact hoặc LTW restart.
- [ ] Đã xác minh log/metric không tiết lộ dữ liệu nhạy cảm và không tạo recursion.

### Trong rollout

- [ ] Tăng tỷ lệ theo giai đoạn, cố định cohort cho workflow cần tính nhất quán.
- [ ] So sánh latency/error/side effects, số match, `proceed()` và mức lan truyền config trên từng nấc.
- [ ] HOLD khi thiếu telemetry/đủ mẫu hoặc khi vi phạm ngưỡng; không tự mở rộng ngoài tiêu chí.
- [ ] Có operator/owner trực và đã chuẩn bị sẵn lệnh rollback, artifact known-good.

### Sau rollback hoặc hoàn tất

- [ ] Xác nhận thực trạng từng instance, JVM/classloader và config revision; không dựa riêng vào “flag đã chuyển”.
- [ ] Kiểm tra hệ thống quay về chỉ số ổn định và business invariants được bảo toàn.
- [ ] Đối soát dữ liệu/tác vụ đã ảnh hưởng; xử lý bù trừ nếu nghiệp vụ yêu cầu.
- [ ] Lưu evidence: trace, log đã ẩn dữ liệu, artifact hash, các bước đã chạy và thời điểm.
- [ ] Bổ sung regression test và điều chỉnh phạm vi pointcut/giới hạn rollout trước lần bật tiếp theo.

**Ghi nhớ:** bật AOP theo giai đoạn là kiểm soát **phạm vi tác động của advice** và **phân bố phiên bản thực tế đang chạy**. Giám sát phải chứng minh nghiệp vụ đúng, còn rollback phải phù hợp với cách aspect được gắn vào luồng thực thi. Một nút OFF chỉ hiệu quả trong phạm vi mà cơ chế AOP và hợp đồng an toàn thực sự cho phép.


</details>

- [Quay lại đầu trang](#back-to-top)
