<a id="back-to-top"></a>

# Annotation và siêu dữ liệu

## Menu
- [Dùng annotation để thể hiện ý định áp dụng hành vi](#annotation-metadata-001)
- [Annotation là siêu dữ liệu, không tự tạo cơ chế AOP](#annotation-metadata-002)
- [Đánh dấu ở cấp kiểu, phương thức hoặc tham số](#annotation-metadata-003)
- [Phạm vi lưu giữ và khả năng đọc siêu dữ liệu](#annotation-metadata-004)
- [Kế thừa annotation và khác biệt giữa khai báo với thực thi](#annotation-metadata-005)
- [Đọc giá trị cấu hình từ annotation trong advice](#annotation-metadata-006)
- [Kết hợp marker tường minh với giới hạn phạm vi](#annotation-metadata-007)
- [Kiểm tra annotation thiếu, mâu thuẫn hoặc đặt sai chỗ](#annotation-metadata-008)
- [Đánh đổi giữa cấu hình bên ngoài và đánh dấu trong mã](#annotation-metadata-009)

## <a id="annotation-metadata-001">Dùng annotation để thể hiện ý định áp dụng hành vi</a>

<details>
<summary>Xem chi tiết</summary>

# Dùng annotation để thể hiện ý định áp dụng hành vi trong AOP

Trong **lập trình hướng khía cạnh (Aspect-Oriented Programming – AOP)**, một câu hỏi quan trọng là: **làm sao đánh dấu những nơi cần một hành vi cắt ngang mà không trộn toàn bộ logic logging, đo thời gian, kiểm toán, kiểm tra quyền hoặc retry vào mã nghiệp vụ?** Một giải pháp thông dụng trong Java là sử dụng **annotation** để thể hiện **ý định (intent)** tại type, method hoặc phần tử chương trình phù hợp, rồi để một cơ chế **pointcut + advice + weaving/interception** diễn giải ý định ấy và áp dụng hành vi.

Ví dụ:

~~~java
@Observed(operation = "submit")
String submit(String orderId);
~~~

Annotation **@Observed** nói rằng invocation này *được chỉ định để quan sát* với nhãn operation là submit. Tuy nhiên, annotation **không tự thực thi logging, không tạo proxy, không kiểm tra quyền và cũng không biến thành advice**. Hành vi thật chỉ xuất hiện khi có một thành phần khác **nhận diện metadata, lựa chọn đúng join point, và can thiệp vào lời gọi** theo một hợp đồng rõ ràng.

Bài thuộc chủ đề **Annotation và siêu dữ liệu**. Nội dung trình bày:
- Annotation thể hiện **điều gì**, ai chịu trách nhiệm **thực thi điều đó**, và đâu là ranh giới của hai phần.
- Cách tạo annotation trong Java, sử dụng **@Target**, **@Retention**, thuộc tính và giá trị mặc định.
- Cách AOP chọn join point dựa trên metadata ở method/type/interface, cùng các bẫy khi annotation nằm ở interface nhưng không có ở implementation.
- Phân biệt marker annotation, annotation có cấu hình, repeatable annotation, meta-annotation và quy tắc kế thừa.
- Ví dụ Java 17 hoàn chỉnh, có thể biên dịch và chạy, minh họa **annotation → interception → advice → business** và những trường hợp không được áp dụng.
- Thiết kế an toàn: thứ tự nhiều aspect, runtime proxy vs weaving, privacy, security, validation, concurrency và kiểm thử.

**Phạm vi là AOP cơ bản và nâng cao nói chung.** Ví dụ dùng **JDK dynamic proxy** như một cơ chế minh họa. Một bộ weaver khác có thể chọn **method call**, **method execution** hoặc cấu trúc khác, và không có bảo đảm mọi nền tảng dùng cùng cú pháp pointcut hoặc cách tìm annotation.

## 1. Đặt annotation vào đúng vai trò

Các khái niệm:
- **Join point:** điểm chương trình có thể can thiệp, chẳng hạn method call hoặc method execution nếu mô hình hỗ trợ.
- **Pointcut:** điều kiện chọn join point; có thể dựa trên tên, type, signature, annotation, đối tượng hoặc điều kiện runtime.
- **Advice:** hành vi thêm vào tại join point phù hợp, như before, after-returning, after-throwing hoặc around.
- **Aspect:** nhóm pointcut/advice cho một concern cắt ngang.
- **Weaving/interception:** đưa advice vào luồng thực thi bằng biên dịch, bytecode, load-time hoặc proxy/wrapper.
- **Annotation:** metadata mô tả ý định hoặc cấu hình, không tự thay đổi cách method thực thi nếu không có thành phần diễn giải.
- **Annotation-driven pointcut:** quy tắc chọn join point dựa vào annotation hiện diện ở những phần tử được định nghĩa rõ.

Sơ đồ:

~~~text
Developer marks method with @Observed
                 |
                 v
       Annotation stored as metadata
                 |
                 v
       Pointcut / metadata resolver
                 |
                 v
     Matching join point occurs?
           /           \
         No             Yes
         |               |
         v               v
   Business only     Advice wraps/calls business
                         |
                         v
                 Result / exception to caller
~~~

Điều này giúp tách biệt hai trách nhiệm:
1. **Business code** tuyên bố operation nào cần concern.
2. **Aspect infrastructure** thực thi concern tương ứng tại những join point có thể quan sát được.

### 1.1 Annotation mô tả ý định, không bảo đảm enforcement

Một developer có thể đặt **@Audited**, **@Timed**, **@Validated** hoặc **@RequiresPermission** trên method. Nếu runtime không có pointcut/advice phù hợp, không có bytecode weaving, hoặc lời gọi **bypass proxy**, metadata vẫn tồn tại mà hành vi có thể **không xảy ra**.

Với logging/metrics, thiếu advice có thể là lỗi observability. Với **authorization**, việc suy rằng “có annotation tức đã chặn quyền” là **lỗi bảo mật**. Các bất biến bắt buộc phải được bảo vệ ở ranh giới mà mọi đường cần bảo vệ đều đi qua và phải có **tests cho đường bypass**.

### 1.2 Tách metadata khai báo và metadata thực thi

~~~text
Intent (declaration):
    @Observed(operation="submit")
    @Timed(metric="order.submit")

Policy/implementation:
    Observe advice
    Timing advice
    Metadata lookup and precedence rules

Invocation:
    Caller -> interception boundary -> applicable advice -> target
~~~

Một annotation có thể khai báo “đo thời gian cho operation này”, nhưng **đo từ lúc nào tới lúc nào** là hợp đồng của advice: call entry, target execution, toàn bộ retry, hay completion của một asynchronous stage? Annotation một mình không trả lời các câu hỏi đó.

## 2. Tạo một annotation đúng mục đích trong Java

Java dùng **@interface** để định nghĩa annotation. Ví dụ tối giản:

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Observed {
    String operation();
    boolean logResult() default false;
}
~~~

Đây là một **mảnh định nghĩa độc lập về cú pháp annotation**, không phải chương trình chạy có main. Hai thuộc tính có ý nghĩa:
- **operation()** là thuộc tính bắt buộc; nơi sử dụng phải cung cấp tên operation.
- **logResult()** mặc định là false, thể hiện lựa chọn thận trọng: không ghi kết quả nghiệp vụ tùy tiện, tránh lộ dữ liệu.

Áp dụng:

~~~java
@Observed(operation = "submit")
String submit(String orderId);

@Observed(operation = "lookup", logResult = false)
String lookup(String orderId);
~~~

Đây là **các chữ ký minh họa**, đặt trong interface hoặc type hợp lệ, không phải các phương thức top-level Java độc lập.

### 2.1 @Target: annotation được dùng ở đâu?

Các phần tử mục tiêu thường liên quan:
- **ElementType.TYPE:** class, interface, enum và các loại khai báo type phù hợp.
- **ElementType.METHOD:** method declaration.
- **ElementType.FIELD:** field declaration.
- **ElementType.PARAMETER:** tham số phương thức.
- **ElementType.CONSTRUCTOR:** constructor.
- **ElementType.ANNOTATION_TYPE:** một loại annotation khác.
- **ElementType.TYPE_USE:** vị trí sử dụng type, thường cần những công cụ hiểu type annotations.

Chọn **@Target(METHOD)** khi ý định gắn cho operation; **@Target(TYPE)** khi muốn đánh dấu một vùng type hoặc một class policy. Một annotation có thể nhận **nhiều target**, nhưng mở rộng target quá mức không tự làm pointcut hiểu thêm loại join point.

**Điểm cần ghi rõ:** annotation trên **field** hoặc **parameter** không tự biến thành annotation trên method. Nếu aspect cần kiểm parameter annotation, phải xác định resolver nào đọc đúng parameter metadata tại đúng join point.

### 2.2 @Retention: annotation tồn tại tới giai đoạn nào?

| Chính sách retention | Ý nghĩa chính | Lựa chọn phù hợp |
| --- | --- | --- |
| **SOURCE** | Chỉ có ở source; không giữ trong class file | Compiler/source checker |
| **CLASS** | Ghi vào class file nhưng không được Java reflection runtime phơi bày theo hợp đồng | Bytecode/static analysis và công cụ build |
| **RUNTIME** | Có ở class file và có thể truy vấn bằng reflection tại runtime | Proxy/interceptor dùng reflection |

Nếu một **JDK dynamic proxy** cần đọc annotation bằng **Method.getAnnotation**, annotation phải có **RUNTIME retention**. Nếu quy tắc chỉ được kiểm tại compile-time bằng processor, có thể dùng retention phù hợp với công cụ và mục tiêu.

**Không mặc định CLASS-retained annotation hiện diện qua reflection runtime.** Ngược lại, RUNTIME retention **không** tự kích hoạt advice: nó chỉ khiến metadata có thể được đọc.

### 2.3 Annotation element không là object cấu hình tự do

Các kiểu phần tử annotation Java được phép giới hạn: primitive, String, Class, enum, annotation và mảng một chiều của những loại phù hợp. Không khai báo một arbitrary service object hoặc mutable Map như giá trị annotation.

Một số nguyên tắc:
- Dùng **enum** cho tập giá trị hữu hạn cần kiểm.
- Tránh magic strings nếu cùng tên operation được dùng rộng rãi.
- Giữ dữ liệu cấu hình **ngắn, ổn định và phi nhạy cảm**.
- Không đưa token, password, key hoặc dữ liệu của request vào giá trị annotation.
- Xác định default rõ; ví dụ **logResult=false** giúp giảm nguy cơ vô tình log nội dung nhạy cảm.

Annotation thường mô tả **policy metadata tĩnh**. Ngữ cảnh người dùng hiện tại, trace ID hay tenant đã xác thực là **dữ liệu của invocation runtime**, không nên gói thành thuộc tính annotation cố định trong source.

## 3. Marker annotation và annotation có cấu hình

**Marker annotation** không có element:

~~~java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface IdempotentOperation {}
~~~

Mục đích là đánh dấu một tính chất, ví dụ operation *được thiết kế* theo hợp đồng idempotency. Tuy nhiên, marker này **không tự biến mọi tác vụ thành idempotent**; side effects, deduplication và request key phải được xử lý thực tế.

**Annotation có cấu hình**:

~~~java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Measured {
    String metric();
    boolean includeFailure() default true;
}
~~~

Nó cho phép cùng một advice nhận cấu hình khác nhau theo operation. Đổi lại, cần:
- Validate tên metric không rỗng.
- Quy định metric đo invocation hay attempt.
- Quy định thế nào là “failure”: throw đồng bộ, returned failure object, hay async completion exceptional.
- Không gắn các tham số runtime chứa identity/secret vào tên metric gây cardinality hoặc rò dữ liệu.

**Không biến annotation thành một ngôn ngữ cấu hình quá phức tạp** với hàng chục cờ tương tác. Nếu hành vi phụ thuộc nhiều điều kiện, một policy component rõ có thể dễ test và bảo trì hơn.

## 4. Resolver: annotation trên interface và trên class triển khai

Đây là một bẫy Java/AOP quan trọng.

~~~text
interface Orders {
    @Observed(operation="submit")
    String submit(String id);
}

class OrdersImpl implements Orders {
    public String submit(String id) { ... }
}
~~~

Annotation nằm trên **method khai báo trong interface**. Nó **không tự được reflection của method triển khai nhìn thấy** như một annotation trực tiếp trên implementation method.

Trong proxy dùng interface, **Method** nhận được trong **InvocationHandler** có thể biểu diễn interface method. Nếu đọc **method.getAnnotation(Observed.class)** tại đó, ta thấy annotation. Nhưng nếu chuyển sang **OrdersImpl.class.getMethod(...)** rồi đọc annotation trực tiếp, kết quả có thể là null.

Một mô hình AOP khác có thể chọn join point dựa trên **implementation method** hoặc có metadata resolver tự tìm trong interface/hierarchy. Vì vậy phải định nghĩa rõ **nguồn metadata chuẩn**:
- Annotation trên interface method có được nhận không?
- Annotation trên implementation method có được ưu tiên không?
- Nếu interface và implementation cùng annotate với cấu hình khác, giải quyết thế nào?
- Override và overload có được đọc đúng chữ ký không?
- Bridge/synthetic methods từ generics có được chuẩn hóa về method gốc không?
- Class-level annotation có áp cho toàn bộ method hay chỉ khi một rule cụ thể quy định?

Không được tự cho rằng annotation method trên interface **“được kế thừa”** theo Java reflection. Nền tảng có thể **tự triển khai quy tắc tìm metadata**, nhưng đó là một lựa chọn resolver chứ không phải Java tự sao chép annotation sang mọi method override.

### 4.1 @Inherited có giải quyết annotation method không?

**@Inherited** trong Java có hiệu lực đối với việc tìm **class-level annotation theo superclass chain** khi annotation type được đánh dấu thích hợp. Nó **không tự làm annotation method được kế thừa** sang method override và **không khiến class triển khai tự kế thừa annotation trên interface**.

Ví dụ:

~~~java
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface Category {
    String value();
}
~~~

Đây là một khai báo hợp lệ khi bổ sung import **java.lang.annotation.Inherited** và các import khác như những đoạn ví dụ trước. Một class con có thể được xem là có **Category** từ superclass khi dùng lookup API có semantics inherited, nhưng không được mở rộng suy luận đó sang method-level hoặc interface ancestry.

### 4.2 Không gộp type annotation với method annotation một cách tùy ý

Một class có **@Area("sales")**, một method có **@Observed(operation="submit")**. Ta có thể định nghĩa rule:
- **@Observed** chọn method được quan sát.
- **@Area** cung cấp nhãn bổ trợ của implementation type.
- Nếu thiếu **@Area**, sử dụng nhãn an toàn **unknown** hoặc báo lỗi cấu hình theo policy.

Nhưng nếu chỉ có **@Area**, nó không tự đồng nghĩa **mọi method của class đều phải có Observed**. Muốn hiệu lực type-wide, phải định nghĩa pointcut và precedence cụ thể.

## 5. Ví dụ Java 17 hoàn chỉnh: annotation → proxy → advice

Ví dụ này dùng **JDK dynamic proxy** làm một interceptor minh họa:
- Interface **Orders** khai báo annotation **@Observed** trên **submit** và **cancel**.
- Method **status** không có annotation, nên đi thẳng tới nghiệp vụ.
- Class **OrdersImpl** có annotation **@Area("sales")** để bổ sung một nhãn không nhạy cảm.
- Handler tìm **@Observed** trên interface Method mà proxy đưa vào.
- Handler không in return value nếu **logResult=false**.
- Khi target ném **IllegalArgumentException**, handler unwrap **InvocationTargetException** và **giữ nguyên nguyên nhân** cho caller.
- Gọi **OrdersImpl** trực tiếp sẽ **bypass proxy**, do đó annotation không được áp dụng qua handler.

Lưu toàn bộ mã thành một file **AnnotationIntentDemo.java**:

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class AnnotationIntentDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Observed {
        String operation();
        boolean logResult() default false;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface Area {
        String value();
    }

    interface Orders {
        @Observed(operation = "submit")
        String submit(String id);

        String status();

        @Observed(operation = "cancel")
        String cancel(String id);
    }

    @Area("sales")
    static final class OrdersImpl implements Orders {
        public String submit(String id) {
            System.out.println("BUSINESS submit " + id);
            return "submitted:" + id;
        }

        public String status() {
            System.out.println("BUSINESS status");
            return "ready";
        }

        public String cancel(String id) {
            System.out.println("BUSINESS cancel " + id);
            if ("blocked".equals(id)) {
                throw new IllegalArgumentException("cannot cancel");
            }
            return "cancelled:" + id;
        }
    }

    static Object invokeTarget(Orders target, Method method, Object[] args)
            throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException error) {
            throw error.getCause();
        }
    }

    static Orders wrap(Orders target) {
        return (Orders) Proxy.newProxyInstance(
                Orders.class.getClassLoader(),
                new Class<?>[] { Orders.class },
                (proxy, method, args) -> {
                    Observed intent = method.getAnnotation(Observed.class);
                    if (intent == null) {
                        return invokeTarget(target, method, args);
                    }

                    if (intent.operation().isBlank()) {
                        throw new IllegalArgumentException("empty operation");
                    }
                    Area area = target.getClass().getAnnotation(Area.class);
                    String areaName = area == null ? "unknown" : area.value();
                    System.out.println("ADVICE enter " + intent.operation()
                            + " area=" + areaName);
                    try {
                        Object result = invokeTarget(target, method, args);
                        System.out.println("ADVICE success "
                                + intent.operation()
                                + (intent.logResult()
                                        ? " result=" + result : ""));
                        return result;
                    } catch (RuntimeException error) {
                        System.out.println("ADVICE error " + intent.operation()
                                + " " + error.getClass().getSimpleName());
                        throw error;
                    } finally {
                        System.out.println("ADVICE finally "
                                + intent.operation());
                    }
                });
    }

    public static void main(String[] args) throws Exception {
        System.out.println("INTERFACE annotated=" +
                Orders.class.getMethod("submit", String.class)
                        .isAnnotationPresent(Observed.class));
        System.out.println("IMPLEMENTATION annotated=" +
                OrdersImpl.class.getMethod("submit", String.class)
                        .isAnnotationPresent(Observed.class));

        OrdersImpl raw = new OrdersImpl();
        Orders observed = wrap(raw);

        System.out.println("CASE annotated");
        System.out.println("RESULT " + observed.submit("A1"));

        System.out.println("CASE unannotated");
        System.out.println("RESULT " + observed.status());

        System.out.println("CASE exception");
        try {
            observed.cancel("blocked");
        } catch (IllegalArgumentException error) {
            System.out.println("CALLER " + error.getMessage());
        }

        System.out.println("CASE direct");
        System.out.println("RESULT " + raw.submit("B2"));
    }
}
~~~

Biên dịch và chạy bằng **JDK 17+**:

~~~bash
javac --release 17 AnnotationIntentDemo.java
java AnnotationIntentDemo
~~~

Stdout **đã được kiểm chứng khi biên dịch và chạy Java 17**:

~~~text
INTERFACE annotated=true
IMPLEMENTATION annotated=false
CASE annotated
ADVICE enter submit area=sales
BUSINESS submit A1
ADVICE success submit
ADVICE finally submit
RESULT submitted:A1
CASE unannotated
BUSINESS status
RESULT ready
CASE exception
ADVICE enter cancel area=sales
BUSINESS cancel blocked
ADVICE error cancel IllegalArgumentException
ADVICE finally cancel
CALLER cannot cancel
CASE direct
BUSINESS submit B2
RESULT submitted:B2
~~~

### 5.1 Metadata nằm ở đâu?

Hai dòng đầu là oracle về reflection:
- **INTERFACE annotated=true**: annotation **Observed** có trên method của interface.
- **IMPLEMENTATION annotated=false**: method triển khai trên **OrdersImpl** không có annotation trực tiếp ấy.

Handler cố ý đọc metadata từ **Method của proxy interface**. Nếu chuyển resolver sang method triển khai mà không có chính sách tìm interface, advice có thể **không còn chạy** dù annotation vẫn ở source.

### 5.2 Vì sao submit được quan sát?

Lời gọi đi qua **observed.submit("A1")**, tức qua proxy. Handler đọc **@Observed(operation="submit")**, in entry, gọi target và in success/finally. Caller nhận kết quả **submitted:A1**.

Vì **logResult=false**, advice không in return value. Caller vẫn nhận kết quả; không log kết quả không có nghĩa advice đã làm mất kết quả.

### 5.3 Vì sao status không có advice?

Method **status()** không được gắn **@Observed**, nên handler gọi target mà không in **ADVICE**. Đây là negative test thiết yếu: **có proxy không có nghĩa mọi method đều phải được chọn bởi pointcut annotation-driven**.

### 5.4 Vì sao lỗi vẫn được caller nhận?

Method **cancel("blocked")** là annotated và ném **IllegalArgumentException**. Java reflection bao lỗi từ target bằng **InvocationTargetException**, nên helper **invokeTarget** lấy **getCause()** để chuyển tiếp lỗi nghiệp vụ.

Advice thấy **RuntimeException**, in dòng error/finally, rồi **ném lại đúng lỗi**. Caller in **CALLER cannot cancel**. Tránh nuốt lỗi trong logging advice hoặc tự động biến mọi lỗi thành success vì có thể làm sai transaction và business contract.

### 5.5 Vì sao direct call không có advice?

Lệnh **raw.submit("B2")** gọi object triển khai trực tiếp, không qua proxy, nên không có dòng **ADVICE**. Annotation vẫn hiện diện trên interface nhưng không có handler ở đường gọi này.

Ví dụ chỉ chứng minh đặc điểm **delegation qua JDK proxy**; một cơ chế bytecode weaving trực tiếp tại method execution có thể chặn tập lời gọi khác. **Annotation đánh dấu ý định không đồng nghĩa mọi đường gọi đã được instrument.**

## 6. Annotation-driven pointcut: matching phải được thiết kế rõ

Một rule có thể nói:

~~~text
If method invocation has effective @Observed metadata:
    run observation advice
Else:
    do not run that advice
~~~

Tuy nhiên, chữ **effective metadata** cần định nghĩa. Một hệ thống có thể chọn:
- Annotation trên interface method.
- Annotation trên implementation method.
- Annotation từ superclass method khi có quy tắc kế thừa/override riêng.
- Annotation ở class/type làm quy tắc mặc định.
- Meta-annotation hoặc composed annotation.
- Kết quả kết hợp annotation type-level và method-level.

Không có thuật toán fallback duy nhất áp cho mọi AOP. Với hệ thống lớn, nên có một **metadata resolver tập trung** và các fixture cho từng tình huống, thay vì mỗi aspect tự đọc reflection theo quy tắc khác nhau.

### 6.1 Định nghĩa resolution order trước khi viết advice

Một chính sách minh họa:

~~~text
1. Method-level annotation on implementation method
2. Method-level annotation on declared interface method
3. Type-level annotation on implementation type
4. No annotation -> no advice

Conflict if multiple explicit method annotations differ:
    report configuration error, or use a documented priority
~~~

Đây **không phải quy tắc của Java reflection tự động**, cũng không phải quy tắc mặc định của mọi AOP compiler. Nó là **chính sách gợi ý** mà application có thể lựa chọn nếu cơ chế interceptor cho phép.

Nếu hai interface khai báo cùng signature với annotations khác nhau, hãy viết **test cụ thể**. Một JDK proxy có thể đưa một Method tương ứng với một interface trong những điều kiện nhất định; dựa hoàn toàn vào Method nhận được mà không có quy tắc conflict có thể tạo ambiguity. Nên tránh việc annotation security khác nhau trên các khai báo cạnh tranh.

### 6.2 Annotation trên type không tự đồng nghĩa annotation trên mọi method

Nếu class gắn **@Area("sales")**, đó là metadata type-level. Trong demo, class label chỉ được đọc **khi method đã có @Observed**. Nó không làm **status()** tự được áp dụng Observed.

Muốn class-level opt-in cho mọi method, cần nêu rõ:
- Những method nào được chọn: public only, declared/inherited, constructors?
- Method-level **opt-out** có được cho phép không?
- Khi một method override class policy khác, ưu tiên thế nào?
- Nếu target type không ở runtime vì proxy/interface dispatch, lấy type nào?
- Advice nào phải được áp dụng và theo thứ tự nào?

## 7. Annotation lặp và meta-annotation

### 7.1 @Repeatable: một phần tử có nhiều khai báo

Một method có thể cần nhiều nhãn hoặc rule độc lập. Java hỗ trợ **repeatable annotations** nếu annotation type được khai báo đúng:

~~~java
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(Tags.class)
@interface Tag {
    String value();
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Tags {
    Tag[] value();
}
~~~

Mảnh trên minh họa cơ chế khai báo. Khi sử dụng:

~~~java
@Tag("external")
@Tag("financial")
void transfer() {}
~~~

Đây là method declaration **đặt bên trong một type hợp lệ**. Một metadata resolver muốn lấy **toàn bộ tag** nên cân nhắc **getAnnotationsByType(Tag.class)** thay vì chỉ **getAnnotation(Tag.class)**, vì compiler có thể dùng container annotation cho nhiều Tag.

Repeatable **không đồng nghĩa chạy advice hai lần**. Hai tag có thể là **cấu hình bổ sung** cho một advice duy nhất; có chạy nhiều lần hay không là quy tắc của pointcut/resolver. Cần test duplicates và thứ tự nếu thứ tự có ý nghĩa.

### 7.2 Meta-annotation: annotation mô tả annotation khác

Có thể tạo một nhóm annotation chuyên biệt được đánh dấu bởi một meta-annotation. Nhưng Java không mặc định **getAnnotation(Observed.class)** sẽ tìm xuyên qua mọi custom annotation type rồi coi chúng là Observed.

Muốn có **composed semantics**, resolver phải:
1. Tìm annotation trực tiếp.
2. Kiểm meta-annotations theo một chính sách.
3. Giải quyết alias/attribute conflicts nếu cơ chế có.
4. Xử lý recursion/cycle và duplicates khi đi trên annotation graph.
5. Cache kết quả một cách có kiểm soát.

Một custom annotation đặt trên method nhưng bản thân type đó được đánh dấu bằng một marker khác **chưa đủ** để Java reflection tự gắn marker ấy lên method.

## 8. Nhiều aspect cùng dựa trên annotation

Một method có thể được đánh dấu:

~~~java
@Observed(operation = "submit")
@Measured(metric = "order.submit")
String submit(String id);
~~~

Đây là **chữ ký minh họa** với các annotation được định nghĩa trong phạm vi tương ứng; không phải toàn bộ chương trình.

Giả sử hai aspect cùng match:
- **Observation** ghi nhận outcome.
- **Timing** đo duration.

Nếu chúng là around advice lồng nhau, kết quả:

~~~text
Timing(Observe(Business))
~~~

khác với:

~~~text
Observe(Timing(Business))
~~~

ở vị trí log và phạm vi đo khi một advice short-circuit, retry hoặc bắt exception. Annotation metadata **không tự quyết định precedence**.

Một chính sách cần làm rõ:
- Aspect nào ở ngoài cùng?
- Nếu cache hit trả sớm, những concern bên trong có còn thấy call không?
- Nếu authorization từ chối trước proceed, audit nào vẫn phải ghi?
- Timing đo mỗi attempt hay request-level duration?
- Nếu hai aspect cùng log result, có rò dữ liệu hoặc double counting không?

### 8.1 Security không thể là optional concern vô tình bị bỏ

Nếu dùng **@RequiresPermission** như một annotation intent, đừng chỉ dựa vào nó để giả định việc kiểm soát quyền. Một rule bắt buộc cần:
- Kiểm đúng principal từ nguồn đáng tin, tài nguyên và hành động.
- Chặn mọi ranh giới cần bảo vệ, kể cả direct-call/self-invocation nếu applicable.
- Test success, deny, exception và short-circuit.
- Không đặt cache có thể trả private data trước một kiểm tra quyền bắt buộc.
- Không bỏ security advice theo một guard/feature flag không được phê duyệt.
- Phát hiện configuration thiếu interceptor hoặc annotation resolution sai trong CI/runtime startup khi policy yêu cầu.

**Metadata là dấu hiệu được kiểm tra; quyền là quyết định phải được thực thi.**

### 8.2 Aspect stateless và concurrency

Annotation trên method thường không thay đổi giữa các invocations, nhưng một shared aspect có thể chạy đồng thời. Không giữ **currentAnnotation, currentUser, startTime hoặc lastArgs** trong mutable field dùng chung.

Nếu cần cấu hình annotation, có thể cache **immutable resolved metadata** theo identity Method/type, với đồng bộ và class-loader lifecycle đúng. Dữ liệu riêng invocation như trace ID, arguments và timing start phải nằm trong local/context thuộc invocation ấy.

## 9. Phân biệt compile-time và runtime metadata

Một cách triển khai khác là dùng annotation **chỉ ở build**:
- Annotation processor phân tích khai báo, phát lỗi/cảnh báo hoặc sinh metadata/code.
- Static analyzer hoặc compiler kiểm rule kiến trúc.
- Compile-time weaver chọn join point và dệt advice nếu cú pháp/representation cho phép.

Với **runtime reflection**, annotation phải có retention phù hợp, và handler/resolver phải được gọi thực tế tại interception boundary.

### 9.1 Annotation processor không tự là AOP runtime weaving

Annotation processor có thể tìm **@Observed** và phát:
- ERROR nếu **operation** rỗng hoặc trùng trái policy.
- WARNING nếu annotation áp dụng cho một method không thể instrument theo cơ chế đang chọn.
- Generated metadata registry cho build nếu thiết kế cho phép.

Nhưng processor bình thường **không tự thay mọi method body thành lời gọi advice**. Nếu bài toán cần runtime interception, cần weaving/proxy/instrumentation hoặc một cách lắp ghép tương đương.

### 9.2 Retention và tập công cụ phải tương thích

| Mục tiêu | Retention thường cần | Điều phải xác minh |
| --- | --- | --- |
| Source-only compiler checker | SOURCE có thể đủ | Checker đọc source/annotations đúng pha |
| Bytecode rule/checker | CLASS hoặc RUNTIME | Classfile chứa metadata mà tool đọc được |
| Reflection interceptor | RUNTIME | getAnnotation/isAnnotationPresent thấy đúng element |
| Post-weave structural verifier | Phụ thuộc representation | Introduction đã được áp dụng trước verifier |
| Runtime advice bắt mọi execution | Không do retention quyết định một mình | Weaving coverage/pointcut và đường gọi thật |

Nếu annotation nằm ở interface method nhưng một bytecode weaver chỉ match implementation method annotations, việc đặt **RUNTIME** vẫn không tự sửa khác biệt về nguồn metadata.

## 10. Giải thích vì sao annotation-driven AOP khác decorator tự gọi

Dùng annotation trên method và một wrapper thực thi là **hai việc khác nhau**:
1. Annotation giữ metadata trong source/classfile.
2. Wrapper hoặc weaver quyết định khi nào sử dụng metadata.

Có thể không dùng annotation mà khai báo pointcut theo package/signature. Ngược lại, có thể có annotation nhưng không dùng AOP: một compiler checker hoặc serializer vẫn có thể đọc nó.

Lợi ích của annotation khi phù hợp:
- Intent ở gần nơi khai báo API.
- Review dễ thấy operation nào chịu concern.
- Không rải logging code trong nghiệp vụ.
- Dễ trích xuất metadata để kiểm các quy tắc build-time.
- Có thể định nghĩa behavior theo một policy thống nhất.

Nhược điểm:
- **Ẩn luồng điều khiển** nếu người đọc không biết có advice ngoài method.
- Metadata và interceptor có thể lệch khi refactor.
- Overlap nhiều annotation gây precedence/conflict.
- Runtime reflection hoặc weaving làm debugging phức tạp.
- Có thể tạo **ảo giác an toàn** với annotation bảo mật nếu đường gọi không đi qua interception.

Vì vậy cần tài liệu **“annotation nào → aspect nào → áp dụng ở boundary nào → điều kiện nào”** và kiểm thử cụ thể.

## 11. Thiết kế đặt annotation ở interface hay implementation?

| Vị trí | Lợi ích | Rủi ro/câu hỏi |
| --- | --- | --- |
| Interface method | Thể hiện API contract, caller dễ nhìn | Resolver ở implementation có tự tìm interface không? |
| Implementation method | Gần mã thực thi, policy theo implementation | Proxy Method từ interface có đọc được không? |
| Interface type | Metadata cho toàn bộ contract | Java @Inherited không tự áp dụng từ interface xuống class |
| Concrete class | Phù hợp type-level category/policy | Có match method riêng lẻ không? |
| Superclass method/type | Tái sử dụng metadata trong hierarchy | Override/inheritance lookup khác loại element |
| Parameter | Phù hợp validation/binding một tham số | Cần resolver theo parameter index, không tự là method advice |
| Annotation type (meta) | Nhóm policy/intent | Cần composed-annotation resolver tường minh |

**Không có một vị trí tối ưu chung.** Với AOP dựa trên interface proxy, interface method có thể tự nhiên. Với weaving theo execution của implementation method, implementation annotations có thể phù hợp hơn. Quan trọng nhất là **quy tắc nhất quán và được test**.

### 11.1 Override và overload

Nếu method **submit(String)** có annotation nhưng **submit(long)** không, một matcher dựa vào **method name** thay vì chữ ký có thể chọn nhầm. Nếu subclass override method, annotation method không tự được copy sang method override; resolver cần chính sách inheritance riêng.

Với generics, bridge/synthetic method có thể xuất hiện; một bytecode interceptor cần biết nên kiểm annotation của bridge hay method nguồn. **Không suy kết quả chỉ từ simple name**.

### 11.2 Annotation loại TYPE_USE

Java có thể gắn annotation vào **vị trí dùng type**. Nhưng một type-use annotation trên generic type argument không tự đồng nghĩa annotation trên method declaration. Một aspect chọn method-level annotation không nên âm thầm đọc mọi type-use annotation như marker của method.

## 12. Kiểm thử annotation-driven AOP

Một test suite hữu ích phải tách **metadata resolution** và **interception behavior**.

| Ca kiểm thử | Oracle mong đợi |
| --- | --- |
| Annotated interface method qua proxy | Advice được gọi đúng một lần |
| Unannotated method qua proxy | Không có advice của concern đó |
| Annotated method nhưng gọi raw target | Không có proxy advice trong mô hình delegation |
| Implementation method không annotate | Reflection trực tiếp trên implementation method không thấy annotation interface |
| Method có annotation cả interface và impl | Áp dụng conflict/precedence policy đã công bố |
| Type-level annotation, method chưa annotate | Chọn hoặc không chọn theo rule type-wide, không suy tùy tiện |
| Annotation SOURCE + runtime reflection | Không có metadata phản chiếu runtime |
| Annotation RUNTIME | Resolver thấy đúng element khi được truy vấn |
| Repeatable annotation | Đếm và xử lý đúng các phần tử qua getAnnotationsByType |
| Override/overload | Đúng signature và hierarchy policy |
| Success path | Advice giữ nguyên return value đúng hợp đồng |
| Exception path | Advice thấy lỗi; không nuốt/mất exception |
| Around short-circuit | Inner advice/target có thể không chạy |
| Two annotated aspects | Precedence/metric scope/cleanup đúng |
| Self-invocation | Biết lời gọi qua proxy hay bypass |
| Concurrency | Không lẫn context/metadata giữa hai invocations |
| Async completion | Phân biệt return stage với hoàn tất tác vụ |
| Missing interceptor | Security/required concern bị phát hiện và build/startup không âm thầm PASS |
| Logging data | Không ghi token/PII do cờ logResult sai |
| Clean vs incremental build | Metadata/registry và artifact khớp source mới |

### 12.1 Kiểm cả positive và negative

Một test chỉ xác nhận **annotated submit được log** không đủ. Cần xác nhận:
- **status không log**, vì không có annotation.
- **direct raw call không log**, vì không qua proxy.
- **cancel lỗi vẫn có finally và ném lỗi**.
- **method implementation không tự mang annotation interface**.
- **không có log return value** khi logResult=false.

Các negative controls giúp chứng minh matcher **không chọn quá rộng** và cho thấy chính xác giới hạn instrumentation.

### 12.2 Tách lỗi metadata với lỗi coverage

Khi advice không xuất hiện, kiểm tra theo thứ tự:

~~~text
Was the join point actually executed?
    |
    +-- No -> no advice event expected.
    |
    +-- Yes -> did the call cross the interception boundary?
                    |
                    +-- No -> coverage/bypass issue.
                    |
                    +-- Yes -> is annotation visible to resolver?
                                   |
                                   +-- No -> placement/retention/inheritance issue.
                                   |
                                   +-- Yes -> did pointcut select it?
                                                  |
                                                  +-- No -> matching rule issue.
                                                  +-- Yes -> advice order,
                                                             exception, short-circuit
                                                             or logging issue.
~~~

**Không đổi annotation hoặc precedence ngay lập tức** khi thiếu một dòng log. Thiếu log có thể do exporter bị lỗi, log filter, proxy bypass hoặc một around advice ngoài đã return sớm.

## 13. Quy tắc thiết kế annotation cho API lâu dài

1. **Một annotation, một ngữ nghĩa chính.** Tránh tên chung mơ hồ như @Magic.
2. **Tên phản ánh intent**, không mô tả cơ chế nội bộ có thể thay đổi.
3. **Thuộc tính tối thiểu**; default an toàn với dữ liệu nhạy cảm.
4. **Target chính xác**, không cho dùng ở những nơi resolver không hỗ trợ.
5. **Retention phù hợp** với công cụ đọc metadata.
6. **Validation sớm** cho operation rỗng, cấu hình mâu thuẫn và illegal combinations.
7. **Thứ tự/precedence rõ** khi nhiều annotations áp dụng.
8. **Tương thích refactor**: đổi method/type không âm thầm làm mất pointcut coverage.
9. **Không che giấu side effects**: annotation mang hậu quả retry/transaction/security cần docs và tests.
10. **Không coi marker là bằng chứng enforcement**: luôn kiểm con đường thật.
11. **Giới hạn logging**: không log args/results tùy ý.
12. **Quản lý version metadata** khi annotations được dùng qua module/artifact khác.
13. **Không giữ invocation context trong annotation values**: identity, trace và deadline thuộc runtime.
14. **Kiểm soát performance** nếu có reflection lookup ở hot path; cache immutable metadata đúng scope.
15. **Kiểm tra code generated/bridge methods và introductions** khi công cụ biến đổi cấu trúc.
16. **Kiểm thử advice khi exception và async**, không chỉ happy path.

## 14. Những ngộ nhận phổ biến

1. **“Đặt annotation là hành vi tự chạy.”** Không, annotation là metadata.
2. **“Retention RUNTIME có nghĩa mọi method đều được intercept.”** Không, chỉ làm annotation đọc được bằng reflection.
3. **“JDK proxy sẽ chặn raw target calls.”** Không trong mô hình delegation của demo.
4. **“Annotation method trong interface tự có trên implementation method.”** Không.
5. **“@Inherited áp dụng cho mọi override và interface.”** Không, cơ chế Java giới hạn ở class-level superclass inheritance.
6. **“Annotation type-level đương nhiên áp lên mọi method.”** Chỉ nếu pointcut/resolver quy định.
7. **“Marker @Idempotent làm side effects tự idempotent.”** Không.
8. **“@RequiresPermission đủ thay authorization thực.”** Không.
9. **“Dùng @Observed thì không thể log nhầm dữ liệu.”** Advice vẫn phải tuân thủ privacy.
10. **“Log kết quả luôn hữu ích.”** Có thể lộ bí mật hoặc PII.
11. **“Annotation processor là runtime aspect.”** Không, nó chủ yếu chạy tại compile-time.
12. **“Có hai repeatable annotations nghĩa advice chạy hai lần.”** Tùy resolver và contract.
13. **“getAnnotation tự đọc xuyên mọi meta-annotation.”** Không.
14. **“Cùng method name là cùng annotation intent.”** Overload cần phân biệt signature.
15. **“Mọi bộ weaver xử lý call/execution giống JDK proxy.”** Khác nhau về join point model.
16. **“Một class annotation thay được method-level chính xác.”** Phụ thuộc mục tiêu và quy tắc.
17. **“Một lần test thành công chứng minh security mọi đường.”** Cần negative/bypass paths.
18. **“Annotation tĩnh chứa được request context sống.”** Giá trị annotation không được thiết kế cho dữ liệu động như vậy.
19. **“Aspect dùng chung có thể giữ currentMethod/currentUser trong field.”** Dễ data race giữa các invocations.
20. **“Đổi tên annotation không ảnh hưởng build.”** Metadata resolver, generated registries và binary consumers có thể phụ thuộc API.

## 15. Bài tập tự kiểm tra

**Bài 1.** Một method có @Observed, nhưng caller gọi trực tiếp target thay vì đi qua proxy. Advice có chắc được gọi?

**Đáp:** không trong mô hình proxy delegation. Annotation vẫn tồn tại nhưng handler không được vào. Weaving trực tiếp có thể có phạm vi khác, cần kiểm công cụ cụ thể.

**Bài 2.** Vì sao RUNTIME retention cần thiết cho getAnnotation ở proxy?

**Đáp:** metadata được lưu và có thể đọc bằng reflection khi JVM chạy. SOURCE/CLASS không cung cấp hợp đồng reflection runtime tương đương.

**Bài 3.** Annotation method nằm trên interface. Java reflection trực tiếp trên implementation method có tự thấy nó?

**Đáp:** không. Resolver phải đọc đúng interface declaration hoặc áp dụng quy tắc tìm hierarchy tường minh.

**Bài 4.** @Inherited có tự truyền annotation trên interface method xuống override không?

**Đáp:** không. Nó không giải quyết phương thức/interface theo cách đó.

**Bài 5.** Nếu @Area gắn class, @Observed gắn một method, liệu status() không annotate có phải được quan sát?

**Đáp:** theo policy demo thì không. Area chỉ bổ sung nhãn cho những method đã được Observed chọn.

**Bài 6.** Trong demo, tại sao exception từ cancel không trở thành InvocationTargetException ở caller?

**Đáp:** invokeTarget lấy nguyên nhân bằng getCause() và chuyển tiếp; proxy handler không vô tình giữ lớp bọc reflection.

**Bài 7.** Nếu cần nhiều nhãn trên cùng method, nên lấy metadata bằng gì?

**Đáp:** với Java repeatable annotation, cân nhắc getAnnotationsByType đúng loại, không chỉ getAnnotation và suy số nhãn.

**Bài 8.** Nếu method được đánh dấu @RequiresPermission nhưng không có interceptor, điều gì phải được kiểm?

**Đáp:** kiểm coverage và policy bắt buộc ở build/startup hoặc runtime, cùng test direct/proxy/self-invocation. Không được tin annotation một mình bảo vệ.

**Bài 9.** Vì sao logResult=false là default có ích?

**Đáp:** kết quả có thể chứa thông tin nhạy cảm; không ghi mặc định giúp giảm nguy cơ vô tình lộ dữ liệu.

**Bài 10.** Method trả CompletionStage và advice log success ngay khi stage được trả. Có chắc async operation thành công chưa?

**Đáp:** chưa. Cần quan sát completion và lỗi bất đồng bộ bằng cơ chế thích hợp.

## 16. Kết luận

**Dùng annotation để thể hiện ý định áp dụng hành vi** là một kỹ thuật AOP hữu ích khi ta muốn đánh dấu concern ngay ở API/method nhưng giữ logic thực thi trong aspect độc lập. Để làm đúng:

1. **Định nghĩa annotation có Target/Retention/attributes phù hợp.**
2. **Quy định metadata resolver** tìm annotation ở interface, implementation, type hay hierarchy nào.
3. **Gắn pointcut/advice vào những join point thật sự có thể instrument**, thay vì suy rằng metadata tự chạy.
4. **Bảo toàn contract** về return value, exception, thứ tự nhiều aspect và request-local state.
5. **Kiểm thử cả negative paths**: unannotated method, direct target call, proxy bypass, runtime retention và exception.
6. **Với concern bắt buộc như security/audit, chứng minh enforcement ở mọi đường cần bảo vệ**, không chỉ nhìn thấy một annotation trong source.

Ghi nhớ: **annotation tuyên bố điều muốn áp dụng; pointcut và weaving/interception quyết định nơi nào có thể áp dụng; advice và policy quyết định hành vi thật.** Ba tầng này phải nhất quán thì annotation-driven AOP mới đáng tin.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="annotation-metadata-002">Annotation là siêu dữ liệu, không tự tạo cơ chế AOP</a>

<details>
<summary>Xem chi tiết</summary>

# Annotation là siêu dữ liệu, không tự tạo cơ chế AOP

## 1. Trọng tâm: dấu `@Trace` là một mô tả, không phải bộ máy can thiệp

Trong **Aspect-Oriented Programming (AOP)**, chúng ta muốn mô-đun hóa các concern cắt ngang như logging, tracing, authorization, metrics, caching hoặc transaction boundaries. Ở Java, **annotation** là một cách phổ biến để gắn **siêu dữ liệu (metadata)** vào class, method, field, constructor, parameter hoặc các thành phần được `@Target` cho phép. Nhưng **annotation tự nó không thay đổi luồng thực thi** và **không tự động tạo aspect, pointcut, advice, proxy hoặc bytecode weaving**.

Hãy xem một method có dấu:

~~~java
@Trace
public String send(String id) {
    return "sent:" + id;
}
~~~

Đây là fragment minh họa, không phải chương trình độc lập. Nếu không có **mã hoặc công cụ nhận diện và xử lý `@Trace`**, lời gọi `send("A")` vẫn chỉ chạy thân method như Java thông thường. Dấu `@Trace` không tự in log, không tự đo duration, không tự bắt lỗi và cũng không tự định nghĩa join point.

Muốn có hành vi AOP, phải có một cơ chế thực thi kết nối metadata với hành động:

~~~text
         [SOURCE / CLASSFILE]
             @Trace
                |
                | chỉ khai báo ý định / dữ liệu
                v
       [READER / POINTCUT RULE]
       xác định method được chọn
                |
                v
       [INTERCEPTION MECHANISM]
       proxy / weaver / transformer / dispatcher
                |
                v
          [ADVICE LOGIC]
         before / after / around
                |
                v
       [THỰC THI TARGET THẬT]
~~~

Không có các tầng nhận diện và can thiệp phù hợp, annotation là **metadata**, không phải **runtime behavior**.

### Mục tiêu học tập

Sau bài này, có thể:

1. Diễn giải vai trò của annotation trong AOP theo đúng ranh giới **metadata vs mechanism**.
2. Phân biệt **đánh dấu method**, **đọc annotation**, **chọn join point** và **can thiệp lời gọi**.
3. Phân biệt `RetentionPolicy.SOURCE`, `CLASS`, `RUNTIME` và tác động đến cách đọc.
4. Hiểu `@Target`, annotation elements, class/interface/method placement và giới hạn thừa kế.
5. Viết chương trình Java 17 kiểm chứng annotation tự nó không chạy advice.
6. Nhận diện coverage của proxy và các khác biệt với bytecode weaving.
7. Thiết kế, QA và triển khai annotation-driven aspect đúng hợp đồng, kể cả lỗi/caching/async/concurrency.

Bài tập trung vào **AOP tổng quát**, không đồng nhất annotation với bất kỳ thư viện cụ thể nào.

## 2. Annotation là gì trong Java?

Một annotation là một **khai báo dữ liệu có kiểu** được gắn với phần tử chương trình theo quy tắc Java. Tự định nghĩa:

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@interface Trace {
    String value() default "default";
}
~~~

Đây là fragment khai báo hợp lệ khi được đặt trong file Java thích hợp. Nó nói:

- `@Target(ElementType.METHOD)`: annotation được phép xuất hiện trên method declarations.
- `@Retention(RetentionPolicy.RUNTIME)`: metadata được giữ để reflection API có thể đọc tại runtime.
- `value()`: một annotation element kiểu `String`, với giá trị mặc định.

Không có `before()`, `after()`, `proceed()` hay logic AOP nào tự động sinh ra chỉ từ định nghĩa đó. Mã thực thi có thể dùng `getAnnotation`/`isAnnotationPresent` để kiểm metadata, nhưng **đọc được** chưa phải **can thiệp được**.

### 2.1. Annotation có phải một đối tượng giống class runtime không?

Annotation type trong Java là một interface đặc biệt theo ngữ nghĩa ngôn ngữ, các giá trị có thể được đọc qua `java.lang.annotation.Annotation` và reflection. Khi được lưu với retention thích hợp, runtime có thể tạo biểu diễn annotation để truy cập elements.

Nhưng việc `SomeClass.class.getAnnotation(Trace.class)` trả dữ liệu không chứng minh JVM đã tự chạy một advice. Annotation object biểu diễn **declaration**, không phải một execution interceptor.

### 2.2. Metadata có thể phục vụ rất nhiều mục đích ngoài AOP

- Kiểm lỗi ở compiler hoặc annotation processor.
- Phát sinh source files / class resources trong build.
- Sinh tài liệu/schema.
- Kiểm validation khi một thư viện thực hiện reflection.
- Cấu hình serialization.
- Tạo registry/discovery.
- Chọn join points cho AOP.
- Ghi thông tin hỗ trợ IDE hoặc static analysis.

**Điểm chung:** một **consumer** nào đó phải quyết định metadata có ý nghĩa gì. Cùng một `@Trace` có thể không làm gì trong app A nhưng được một engine riêng diễn giải ở app B; annotation không mang hành vi AOP mặc định.

## 3. Bốn tầng phải phân biệt rõ

| Tầng | Câu hỏi | Ví dụ |
| --- | --- | --- |
| **Khai báo** | Annotation gắn ở đâu, có giá trị nào? | `@Trace("checkout")` |
| **Đọc metadata** | Ai khám phá nó, bằng API nào, ở giai đoạn nào? | Java reflection, compiler processor |
| **Matching / selection** | Rule nào biến metadata thành tiêu chí chọn join point? | Chọn execution method có `@Trace` |
| **Interception / advice** | Điều gì khiến đoạn advice thật sự chạy? | Proxy handler, woven instructions, agent transformer |

Một hệ thống có thể đã làm tầng 1 và 2 mà vẫn chưa có tầng 3/4. Ví dụ:

~~~java
boolean found = method.isAnnotationPresent(Trace.class);
~~~

Câu lệnh `found == true` **không** ép lần gọi `method.invoke()` tiếp theo phải chạy logging. Người viết phải xây wrapper, dispatcher hoặc weave bytecode theo rule đã định.

### 3.1. Trường hợp “có annotation nhưng không có aspect”

~~~text
Caller ---> annotated target ---> normal return

Không có proxy/advice/weaver:
  Không có AOP behavior mặc định.
~~~

Ngay cả khi annotation có retention `RUNTIME`, JVM vẫn không được yêu cầu chạy logic before/after khi call method đó.

### 3.2. Trường hợp “có aspect nhưng không cần annotation”

AOP cũng có thể chọn bằng **method signature**, type, package pattern, control flow hoặc điều kiện khác mà engine hỗ trợ:

~~~text
Select execution of all methods named charge(...),
regardless of whether @Trace is present.
~~~

Đây là **pseudocode ý niệm**, không phải cú pháp bắt buộc. Vì vậy câu “AOP phải có annotation” cũng sai. Annotation chỉ là **một trong nhiều nguồn dữ liệu để chọn điểm can thiệp**.

## 4. Retention: metadata nằm ở giai đoạn nào?

Một annotation không thể được đọc ở mọi thời điểm chỉ vì nó tồn tại trong source. **Retention phải phù hợp với consumer thực tế**; đây là điều kiện để metadata có thể tham gia lựa chọn join point, chưa phải bằng chứng advice được chạy.

| Retention | Consumer phù hợp | Có thể đọc bằng Java reflection runtime? |
| --- | --- | --- |
| `SOURCE` | Compiler hoặc annotation processor còn nhìn thấy source | Không |
| `CLASS` | Công cụ phân tích classfile hoặc binary weaver hỗ trợ thuộc tính annotation | Không qua API reflection annotation thông thường |
| `RUNTIME` | Runtime reflector/interceptor như `Method.getAnnotation` | Có, **trên đúng declaration** được truy vấn |

**Mặc định không viết `@Retention` là `CLASS`**, không phải `RUNTIME`. Một `SOURCE` marker có thể rất hữu ích để validate hoặc sinh code khi build; `CLASS` vẫn có thể được bytecode tooling đọc. Ngược lại, nếu handler JDK dùng `Method.isAnnotationPresent`, hãy dùng `RUNTIME` và kiểm tra đúng method/interface/implementation.

Sau cùng, thêm `RUNTIME` chỉ làm annotation **có thể đọc được**. Muốn AOP hoạt động vẫn cần rule lựa chọn, cơ chế interception/weaving, advice được đăng ký và đường gọi thực tế tới join point.

## 5. Target và phần tử được phép đánh dấu

`@Target` giới hạn vị trí hợp lệ của annotation, không quy định advice chạy khi nào.

Những `ElementType` thường gặp:

| Vị trí | Ý nghĩa declaration | Cần tránh suy diễn |
| --- | --- | --- |
| `TYPE` | Class/interface/enum/annotation type | Không tự áp method-level advice |
| `METHOD` | Method declaration | Không tự can thiệp method calls |
| `CONSTRUCTOR` | Constructor declaration | Không tự tạo constructor join point ở mọi engine |
| `FIELD` | Field declaration | Không có nghĩa field read/write đã được intercept |
| `PARAMETER` | Parameter metadata | Không tự chạy validation |
| `ANNOTATION_TYPE` | Meta-annotation | Không tự làm các annotation khác được quét đệ quy |
| `TYPE_USE` | Type usage | Không đồng nghĩa runtime method join point |
| `TYPE_PARAMETER` | Type parameter declaration | Không tự tạo business policy |
| `PACKAGE` / `MODULE` | Package/module declarations | Không tự tạo broad interception rule |
| `RECORD_COMPONENT` | Record component | Không tự can thiệp record accessor |

`@Target(TYPE)` trên một class và `@Target(METHOD)` trên method có thể cần **hai cách matching khác nhau**. Một engine có thể chọn mọi methods của class có annotation, trong khi engine khác chỉ chọn method trực tiếp được đánh dấu. **Không tự suy một rule từ vị trí annotation**.

### 5.1. Method annotation khác class annotation

~~~java
@Feature("payments")
class PaymentService {
    @Trace
    public void settle() { }
    public void status() { }
}
~~~

Đây là fragment minh họa: `@Feature` và `@Trace` cần khai báo phù hợp. `@Feature` ở class không đồng nghĩa `settle()` và `status()` đều có direct `@Feature` trên từng `Method`. Một rule “methods nằm trong class có `@Feature`” phải kiểm class metadata; một rule “method có `@Trace`” phải kiểm method metadata.

## 6. Ví dụ Java 17 hoàn chỉnh: annotation đứng yên cho tới khi có proxy

Chương trình sau dùng **JDK thuần** và viết rõ hai thành phần:

1. `@Trace` là annotation retention `RUNTIME` gắn vào implementation method `Core.run(String)`.
2. `AnnotationAopDemo.instrument(...)` tạo **JDK dynamic proxy**. Handler đọc annotation trên **method implementation** và chỉ với method được chọn mới in `ADVICE before/after`.

Bên cạnh đó còn có `@BuildMark` với `RetentionPolicy.CLASS`, `@SourceMark` với `RetentionPolicy.SOURCE` và `@Feature` trên class để so sánh reflection.

Các đường gọi cần quan sát:

- **Direct:** `core.run("A")` được đánh dấu nhưng **không có advice**, vì caller gọi raw core.
- **Proxy:** `proxy.run("B")` chạy handler; handler thấy `@Trace` và in advice trước/sau.
- **Không match:** `proxy.plain()` vẫn qua proxy nhưng không có `@Trace` nên không in advice.
- **Self-invocation:** `proxy.outer("C")` đi qua proxy ở `outer()`, nhưng `Core.outer()` gọi `this.run("C")` trực tiếp trong target, bypass proxy cho lời gọi bên trong.
- **Retention:** `RUNTIME` annotation được reflection nhìn thấy; `CLASS` và `SOURCE` không.
- **Assertion:** cả ba lần business `run` thật đều xảy ra, trong khi advice chỉ xuất hiện ở lời gọi qua proxy được đánh dấu.

**Giới hạn:** đây là một **proxy-based interception demo**, không phải compile-time/load-time weaving, không có pointcut language engine đầy đủ. Kết luận được chứng minh là **annotation chỉ mang metadata, còn proxy handler mới triển khai điều kiện và hành vi**.

Tạo file `AnnotationAopDemo.java`:

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public final class AnnotationAopDemo {
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Trace {}

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.CLASS)
    @interface BuildMark {}

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.SOURCE)
    @interface SourceMark {}

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Feature { String value(); }

    interface Operation {
        String run(String id);
        String plain();
        String outer(String id);
    }

    @Feature("orders")
    static final class Core implements Operation {
        int runs;
        @Trace @BuildMark @SourceMark
        public String run(String id) {
            runs++;
            System.out.println("CORE run " + id);
            return "ok:" + id;
        }
        public String plain() {
            System.out.println("CORE plain");
            return "plain";
        }
        public String outer(String id) {
            System.out.println("CORE outer");
            return this.run(id);
        }
    }

    static Object invoke(Object target, Method m, Object[] args) throws Throwable {
        try {
            return m.invoke(target, args);
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
    }

    static Operation instrument(Operation target) {
        return (Operation) Proxy.newProxyInstance(
            Operation.class.getClassLoader(),
            new Class<?>[] { Operation.class },
            (proxy, interfaceMethod, args) -> {
                Method implementation = target.getClass().getMethod(
                    interfaceMethod.getName(), interfaceMethod.getParameterTypes());
                boolean selected = implementation.isAnnotationPresent(Trace.class);
                if (!selected) return invoke(target, implementation, args);
                System.out.println("ADVICE before " + implementation.getName());
                Object result = invoke(target, implementation, args);
                System.out.println("ADVICE after " + result);
                return result;
            });
    }

    static void check(boolean value) {
        if (!value) throw new AssertionError("check failed");
    }

    public static void main(String[] args) throws Exception {
        Method run = Core.class.getMethod("run", String.class);
        boolean runtime = run.isAnnotationPresent(Trace.class);
        boolean build = run.isAnnotationPresent(BuildMark.class);
        boolean source = run.isAnnotationPresent(SourceMark.class);
        check(runtime && !build && !source);
        System.out.println("REFLECTION runtime=true class=false source=false");
        System.out.println("FEATURE " + Core.class.getAnnotation(Feature.class).value());

        Core core = new Core();
        System.out.println("DIRECT " + core.run("A"));
        Operation proxy = instrument(core);
        System.out.println("PROXY " + proxy.run("B"));
        System.out.println("PLAIN " + proxy.plain());
        System.out.println("OUTER " + proxy.outer("C"));
        check(core.runs == 3);
        System.out.println("ASSERT runs=3");
        System.out.println("ALL ASSERTIONS PASSED");
    }
}
~~~

Biên dịch bằng API tương thích Java 17 trở lên:

~~~bash
javac --release 17 -encoding UTF-8 AnnotationAopDemo.java
java AnnotationAopDemo
~~~

Đầu ra đã được kiểm chứng bằng `javac --release 17` và JVM:

~~~text
REFLECTION runtime=true class=false source=false
FEATURE orders
CORE run A
DIRECT ok:A
ADVICE before run
CORE run B
ADVICE after ok:B
PROXY ok:B
CORE plain
PLAIN plain
CORE outer
CORE run C
OUTER ok:C
ASSERT runs=3
ALL ASSERTIONS PASSED
~~~

### 6.1. Vì sao `DIRECT ok:A` không có advice?

`core` là **target object gốc**. Lệnh `core.run("A")` không đi qua `instrument(...)` proxy. JVM không tự phát hiện `@Trace` rồi chèn lệnh in `ADVICE`, dù annotation có `RUNTIME` retention.

Đây là bằng chứng trực tiếp nhất cho câu **annotation là metadata, không tự tạo AOP**.

### 6.2. Vì sao `PROXY ok:B` có advice?

Caller gọi `proxy.run("B")`. `InvocationHandler` được JVM proxy machinery gọi, sau đó handler dùng reflection:

~~~java
Method implementation = target.getClass().getMethod(
    interfaceMethod.getName(), interfaceMethod.getParameterTypes());

boolean selected = implementation.isAnnotationPresent(Trace.class);
~~~

Đây là fragment cắt từ chương trình đầy đủ. `selected` là true vì `Core.run` có `@Trace`. **Handler** chủ động in before, gọi method thật và in after. Chính handler cung cấp behavior; annotation chỉ giúp nó quyết định **có chọn method hay không**.

### 6.3. Vì sao `plain()` không in advice?

`proxy.plain()` vẫn đi qua proxy. Handler tìm `Core.plain()` nhưng method đó không có `@Trace`. Điều kiện false, nên handler gọi target mà không in dòng advice. Đây là một **negative matching test** quan trọng: kiểm đúng việc proxy tồn tại không đồng nghĩa mọi method đều chịu cùng behavior.

### 6.4. Vì sao `outer("C")` bypass advice ở `run`?

`proxy.outer("C")` tới handler, nhưng `outer` không có `@Trace`. Target `Core.outer` được gọi, rồi bên trong nó thực hiện `this.run(id)` trên **chính core object**, không đi qua `proxy`.

Vì vậy `CORE run C` xuất hiện, còn `ADVICE before run` không xuất hiện lần hai. Đây là một giới hạn **đường gọi proxy**; một cơ chế dệt bytecode vào execution join point có thể có coverage khác, nếu thực sự instrument đúng class/method.

### 6.5. Vì sao `BuildMark` và `SourceMark` không thấy bằng reflection?

`BuildMark` dùng `RetentionPolicy.CLASS`: classfile có thể giữ metadata, nhưng Java reflection thông thường không đưa nó vào `isAnnotationPresent`. `SourceMark` chỉ ở source nên không còn trong classfile sau compile. `Trace` dùng `RUNTIME` nên runtime method reflection có thể đọc.

**Không suy** rằng classfile scanner sẽ thấy kết quả hệt runtime reflection; hai consumers dùng hai cấp dữ liệu khác nhau.

### 6.6. Vì sao phải unwrap `InvocationTargetException`?

`Method.invoke()` đóng gói exception target ném trong `InvocationTargetException`. Handler lấy `getCause()` để giữ exception target theo đường caller trong giới hạn hợp đồng proxy. Nếu không xử lý, caller có thể thấy loại wrapper khác hoặc exception handling bị thay đổi ngoài ý muốn.

**Chính sách exceptions của advice** luôn là một phần hợp đồng: observer không nên vô tình che hoặc biến lỗi nghiệp vụ.

## 7. Annotation placement: interface khác implementation

Một lỗi phổ biến khi dùng interface proxy:

~~~java
interface Operation {
    @Trace
    String run(String value);
}

final class Core implements Operation {
    public String run(String value) {
        return "ok:" + value;
    }
}
~~~

Đây là fragment minh họa, yêu cầu `@Trace` được khai báo phù hợp.

Tại đây annotation nằm trên **interface declaration**, không phải direct annotation của implementation method. Nếu handler chỉ gọi:

~~~java
target.getClass().getMethod("run", String.class)
      .isAnnotationPresent(Trace.class);
~~~

thì **không được mặc định** kết quả true. Method annotations không tự được kế thừa từ interface method sang implementation method theo logic reflection thông thường.

Ngược lại, nếu `@Trace` gắn trên implementation method như demo, nhưng handler chỉ kiểm **interfaceMethod** từ proxy, nó cũng có thể không thấy dấu ấy.

### 7.1. Muốn hỗ trợ cả hai vị trí phải định nghĩa rule

Một scanner có thể chọn:

1. Chỉ annotation ở implementation method.
2. Chỉ annotation ở interface declaration.
3. Ưu tiên implementation rồi fallback interface.
4. Gộp metadata từ interface, superclass và implementation với precedence do hệ thống quy định.
5. Chỉ match type-level annotation rồi áp cho methods.

Không có policy thống nhất bắt buộc cho mọi AOP engine. Nếu chọn gộp, phải quyết định **giá trị nào thắng khi mâu thuẫn**, xử lý overload/bridge methods/generics ra sao và test method resolution thật.

### 7.2. Không nhầm annotation lookup với pointcut execution coverage

Có thể scanner tìm đúng `@Trace` nhưng target call không qua proxy, nên advice vẫn không chạy. Cũng có thể weaving đã instrument target nhưng matcher lấy metadata ở vị trí khác mong đợi, nên advice không được chọn.

**Hai câu hỏi riêng**:

- `selected(method)` có đúng không?
- `intercepted(invocation)` có thật không?

Chỉ khi cả hai đúng, annotation-driven advice mới phát tác dụng.

## 8. `@Inherited` và các giới hạn về thừa kế annotation

Java có meta-annotation `@Inherited` dùng cho **annotation types**, nhưng nó không phải một quy tắc “mọi annotation của mọi member được tự động kế thừa”.

Điểm then chốt:

- `@Inherited` có ý nghĩa đặc biệt cho **class-level annotation lookup** trong những API reflection phù hợp.
- Nó **không** có nghĩa method annotations tự chảy từ superclass/interface methods sang overriding methods.
- Nó **không** tự tạo AOP interception cho subclass.
- Nó **không** tự làm annotations trên interface được kế thừa bởi implementing class theo cùng cơ chế class inheritance.
- Một engine tự xây metadata traversal qua interfaces/superclasses có thể cung cấp policy riêng.

Ví dụ ý niệm:

~~~java
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface TrackedType {}

@TrackedType
class Parent {}

class Child extends Parent {}
~~~

Đây là declaration fragment hợp lệ trong một file Java với imports phù hợp. Khi `TrackedType` được đánh dấu `@Inherited`, class-level reflection có thể thấy annotation inherited trên `Child` theo quy tắc Java. Nhưng không nên suy rằng mọi method `Child` có annotation `TrackedType` hoặc đã được weave.

### 8.1. Vì sao điều này quan trọng cho AOP?

Một rule match “class có `@TrackedType`” phải định nghĩa có dùng `getDeclaredAnnotation` hay `getAnnotation`, có xét interface metadata hay không. Khác lookup policy dẫn đến khác **pointcut coverage**, dù source classes y hệt nhau.

## 9. Meta-annotations và composed annotations

Một annotation cũng có thể mang annotations khác:

~~~java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Trace
@interface Audited {}
~~~

Đây là fragment minh họa: `@Trace` trong ví dụ cần `@Target(ANNOTATION_TYPE)` hoặc một tập target bao annotation types mới hợp lệ, khác với `@Trace` method-only ở chương trình Java trước. Không trộn hai khai báo trái target trong cùng file.

Quan trọng hơn: nếu một method được đánh dấu `@Audited`, một scanner chỉ gọi:

~~~java
method.isAnnotationPresent(Trace.class)
~~~

**không tự động tìm đệ quy `@Trace` trên annotation type `Audited`**. Muốn hỗ trợ composed annotations, consumer phải viết hoặc dùng logic **meta-annotation traversal**, có policy về vòng lặp, alias attributes và precedence.

### 9.1. Phân biệt “meta-annotation có tồn tại” và “được hiểu là alias”

Java reflection có thể cho phép khám phá annotations trên annotation type khi chúng có retention phù hợp. Nhưng **cách diễn giải** chúng như `@Trace` gián tiếp vẫn do scanner/engine quyết định.

Một số câu hỏi cần thiết kế:

- Chỉ scan direct annotation hay duyệt transitive meta-annotations?
- Nếu cùng metadata xuất hiện trực tiếp và gián tiếp, giá trị nào ưu tiên?
- Nếu hai composed annotations cung cấp giá trị mâu thuẫn, xử lý ra sao?
- Có hỗ trợ repeatable container không?
- Có cache kết quả scan theo đúng `ClassLoader` và version không?

Đừng coi “đã có composed annotation” là bằng chứng aspect chắc chắn nhận nó.

## 10. Repeatable annotations cũng cần consumer hiểu

Java cho phép khai báo annotation có `@Repeatable` cùng container type thích hợp. Khi có nhiều annotation cùng loại trên một element, API như `getAnnotationsByType(...)` có thể gom các lần xuất hiện theo hợp đồng reflection.

Nhưng một pointcut/processor chỉ dùng `getAnnotation(...)` hoặc kiểm một direct annotation chưa chắc cho kết quả mà người thiết kế mong muốn với repeatable forms. **Cấu trúc dữ liệu metadata** và **policy matching** vẫn phải được kiểm đúng.

Ví dụ một annotation `@Permission("READ")` và `@Permission("WRITE")` có thể cần ý nghĩa AND hoặc OR; **annotation không tự định nghĩa runtime authorization semantics**. Phải viết rule và security tests.

## 11. Annotation processor khác runtime AOP

**Annotation processor** hoạt động trong luồng compilation để đọc annotation, báo lỗi, tạo source/resources hoặc artifacts theo API hỗ trợ. Nó không tự động là một around advice ở runtime.

~~~text
SOURCE @Marker
    |
    v
COMPILER / ANNOTATION PROCESSOR
    |
    +-> diagnostics / generated source / metadata resources
    |
    v
compiled application
~~~

Nếu processor sinh ra wrapper hoặc code gọi interceptor, **generated code** đó mới thực hiện hành vi khi ứng dụng chạy. Nếu processor chỉ báo cảnh báo hoặc sinh tài liệu, không có runtime interception.

### 11.1. Compile-time weaving cũng là cơ chế riêng

Một compile-time weaver có thể sử dụng metadata để xác định nơi cần chèn instructions vào class hoặc source transformation. Trong trường hợp đó:

- Annotation có thể là **input** của rule.
- Weaver là **mechanism** áp dụng transformation.
- Code đã được dệt mới là thứ **thực hiện advice** khi join point chạy.

Chỉ annotation trong source không tự làm compiler vanilla tạo aspect.

### 11.2. Binary weaving có thể dùng retention `CLASS`

Một binary weaver đọc bytecode có thể thấy annotation retention `CLASS` nếu classfile chứa nó và tool có logic đọc. Runtime reflection thông thường không phải nguồn dữ liệu duy nhất cho aspect matching.

Đây là lý do phải biết **tool đọc annotations vào lúc nào** trước khi chọn retention.

### 11.3. Load-time weaving có lifecycle riêng

Một load-time transformer có thể can thiệp classfile trước khi JVM định nghĩa class, nhưng:

- Transformer/agent phải thực sự được cài và có quyền xem class.
- Classloader, module boundaries và timing ảnh hưởng coverage.
- Một class đã load trước khi transformer hoạt động có thể không được xử lý theo plan.
- Hot retransformation có ràng buộc schema riêng.
- Một target dù có annotation vẫn không tự xuất hiện trong danh sách class được transform.

Như vậy **annotation được giữ** chưa đủ; còn phải **engine có chạy và match đúng**.

## 12. Annotation-driven AOP: sơ đồ thực thi an toàn hơn

Một thiết kế rõ ràng thường có các trách nhiệm tách biệt:

~~~text
1. DECLARATION
   Define @Observed with retention/targets/attributes

2. DISCOVERY
   Locate eligible classes/methods and read metadata

3. RESOLUTION
   Resolve implementation vs interface, inheritance, overloads, bridge methods

4. MATCHING
   Select eligible join points using explicit policy

5. INSTALLATION
   Create proxy / apply weaving / register interceptor

6. INVOCATION
   Advice before/after/around runs at selected boundaries

7. VERIFICATION
   Assert both positive and negative coverage on release artifact
~~~

Bỏ qua tầng 5 là lỗi rất thường gặp: scanner đã tìm metadata, log “registered 100 methods”, nhưng caller vẫn gọi raw objects nên không có interception.

### 12.1. Thứ tự cài đặt và lifecycle

- Một proxy chỉ có tác dụng khi caller giữ **reference tới proxy**, không phải raw target.
- Một structural/weaving transformation cần áp dụng lên **đúng class và join point** trước/ở thời điểm công nghệ hỗ trợ.
- Việc tạo aspect instance, load class và invoke advice là **các mốc khác nhau**.
- Đổi annotation value có thể yêu cầu rebuild/reweave/reload tùy cơ chế.
- Nếu cache metadata scan, cần chính sách invalidation khi code hoặc loader thay đổi.

## 13. `@Target` không đảm bảo join point nào có thể được dệt

Nếu tạo:

~~~java
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@interface Sensitive {}
~~~

điều đó chỉ cho phép `@Sensitive` gắn trên field. Nó **không** làm mọi field get/set tự trở thành join points mà tool có thể intercept. Một interface proxy thường chỉ thấy method invocations đi qua wrapper; nó không tự nhìn thấy mọi direct field read.

Tương tự:
- Annotation trên constructor không ép proxy method interceptor quan sát constructor execution.
- Annotation trên private method không làm JDK interface proxy có thể intercept private method.
- Annotation trên class không làm static initialization trở thành một advice event.
- Annotation trên parameter không tự validation hoặc convert input.

**Khả năng join point model** và **tính hợp lệ của vị trí annotation** là hai lớp quy tắc khác nhau.

## 14. Proxy, weaving và đường bypass

Một annotated method không tạo behavior trên mọi đường gọi:

| Đường gọi | JDK interface proxy thường thấy? | Ý nghĩa |
| --- | --- | --- |
| Caller gọi `proxy.run()` | Có | Handler có thể kiểm annotation và chạy advice |
| Caller gọi `core.run()` raw | Không | Direct execution bypass proxy |
| `core.outer()` gọi `this.run()` | Không qua proxy cho inner call | Self-invocation bypass |
| Caller gọi private method | Không phải interface proxy method | Cần engine khác nếu muốn instrument |
| Caller đọc field trực tiếp | Không | JDK proxy không tự intercept field access |
| Constructor tạo target | Không mặc nhiên | Proxy được tạo sau theo design |
| Bytecode-woven execution | Tùy engine/instrumentation thực | Có thể bắt được những nơi proxy không thấy |
| Load-time transformer bỏ lỡ class | Không có weaving ở class ấy | Annotation không thể tự bù coverage |

**Không đánh đồng** kết quả của JDK proxy demo với mọi compile-time/binary/load-time weaving engine. Mỗi công nghệ có phạm vi join points và chi phí khác nhau.

### 14.1. Tại sao annotation ở implementation dễ bị bỏ sót?

InvocationHandler nhận `Method` từ proxy interface. Nếu annotation ở implementation method, phải resolve target implementation tương ứng. Với overloads, generics/bridge methods và inheritance, việc lấy implementation method không đơn giản là so mỗi tên.

### 14.2. Cẩn thận với object methods

`equals`, `hashCode` và `toString` có thể đi qua proxy handler. Nếu code mặc định coi mọi invocation đều là business method có một String argument, nó có thể ném lỗi. Demo chỉ tập trung vào ba methods đã khai báo; production handler phải định nghĩa chính sách cho Object methods và corner cases.

## 15. Hợp đồng của annotation phải được đặc tả rõ

Một annotation `@Audited` có thể là **nhãn dữ liệu**, hoặc được một công cụ diễn giải theo contract cụ thể. Muốn dùng làm API của AOP, cần viết rõ:

- **Target:** đặt trên method, type, annotation type hay field?
- **Retention:** ai cần nhìn thấy metadata?
- **Parameters:** các attributes có giá trị hợp lệ nào?
- **Matching:** direct annotation hay recursive lookup/meta-annotation?
- **Scope:** implementation vs interface, superclass vs declared method?
- **Interception:** proxy, weaver hoặc dispatcher?
- **Order:** advice này đứng trước/sau authorization/cache/retry ra sao?
- **Exceptions:** advice có được thay đổi lỗi của target không?
- **Concurrency:** advice state có per-invocation hay shared không?
- **Asynchronous completion:** theo method return hay Future completion?
- **Security:** có đường bypass raw/direct/async không?
- **Failure policy:** metadata invalid, advice sink down, scanning failure xử lý thế nào?
- **Versioning:** xóa/đổi annotation value có ảnh hưởng clients/build artifacts không?

Không có hợp đồng này, dấu `@Audited` dễ tạo ra **cảm giác an toàn giả**.

## 16. Ví dụ ứng dụng: `@RequiresPermission` không phải kiểm quyền tự động

Giả sử method có:

~~~java
@RequiresPermission("TRANSFER")
public void transfer() {
    moveFunds();
}
~~~

Đây là fragment khái niệm; annotation và `moveFunds()` cần được định nghĩa. Không có security enforcement chỉ vì annotation đã được đặt. Một người gọi trực tiếp `transfer()` trên raw target có thể bỏ qua proxy nếu chỉ proxy chịu trách nhiệm kiểm quyền.

**Vì vậy:** một policy bảo mật quan trọng phải có ranh giới enforcement đáng tin. Đừng dựa duy nhất vào annotation như một thứ “tự bảo vệ” method. Cần test cả proxy path, raw path, self-invocation, async/remote entry points và điều kiện loader/weaving.

### 16.1. Đừng nhầm annotation-driven audit với transaction

`@Audited` ở `save()` không tự đảm bảo event `BUSINESS_COMMITTED` thật sự xảy ra sau database commit. Một after-returning advice có thể chạy **trước khi** outer transaction commit; hoặc advice gửi audit ngoài ngay sau khi nghiệp vụ đã commit rồi lỗi.

Annotation không tạo tính nguyên tử, idempotency hoặc durability. Những tính chất đó thuộc **tài nguyên và workflow** được thiết kế.

### 16.2. Đừng nhầm annotation-driven caching với tính thuần

`@Cached` không chứng minh method pure, deterministic hay không có side effects. Nếu around advice short-circuit mà không gọi target, hành vi nghiệp vụ có thể khác kỳ vọng. Cần đặc tả cache key, expiration, concurrency và exceptions.

**Hành vi chỉ xuất hiện khi có mechanism** và phải được kiểm theo hợp đồng nghiệp vụ chứ không chỉ theo syntax annotation.

## 17. Làm gì khi metadata không tồn tại hoặc sai?

Các trường hợp:

1. Annotation không có trên method.
2. Annotation có nhưng `RetentionPolicy.CLASS`; runtime reflection không đọc được.
3. Annotation nằm trên interface nhưng scanner chỉ kiểm implementation.
4. Annotation nằm trên implementation nhưng scanner chỉ kiểm interface.
5. Annotation nằm trên superclass nhưng scanner không duyệt hierarchy.
6. Annotation nằm trên meta-annotation nhưng scanner chỉ kiểm direct annotation.
7. Annotation element value không hợp lệ theo domain.
8. Runtime nạp class khác version chưa có annotation.
9. Method generic được gọi qua bridge/overload khác matcher dự kiến.
10. Scanning gặp classloader/module visibility giới hạn.

Mỗi trường hợp phải có policy: **không match**, **fail-fast khi khởi động**, **cảnh báo**, **fallback** hoặc **từ chối request** tùy độ quan trọng. Với security, lựa chọn fail-open do “không thấy metadata” có thể không chấp nhận được.

## 18. Performance: metadata lookup cũng có chi phí

Một implementation đơn giản có thể làm mỗi invocation:

~~~text
get implementation Method
read annotation metadata
check conditions
invoke target
~~~

Dưới tải lớn, reflection lookup/method resolution lặp lại có thể thêm overhead. Có thể cache metadata scan theo method/type:

- Cần dùng key phân biệt **method signature, declaring class, proxy interface, classloader**.
- Classloaders động có thể bị giữ lại nếu cache static giữ `Method`/`Class` quá lâu.
- Module/plugin reload cần invalidation hoặc weak references thích hợp.
- `Method` reflection cache không tự giải quyết vấn đề **pointcut coverage**.
- Nếu scan chỉ chạy khi startup, đừng đánh đồng chi phí startup với overhead mỗi invocation.
- Một advice ghi network log có thể tốn hơn metadata lookup rất nhiều; đo trên workload đại diện.

**Đừng tối ưu bằng cách bỏ kiểm policy.** Với authentication/authorization, correctness có ưu tiên cao hơn vài microseconds; nên thiết kế cache đúng và test concurrent behavior.

### 18.1. Annotation metadata không phải invocation-local state

`@Trace` là metadata dùng chung của method/type; correlation ID, start time hoặc request context thường là **state theo invocation**. Đừng lưu `startTime` trên singleton handler chỉ vì annotation gắn trên method; requests song song có thể ghi đè state.

Ví dụ:

~~~java
long started = System.nanoTime();
try {
    return proceed();
} finally {
    elapsed.record(System.nanoTime() - started);
}
~~~

Đây là fragment minh họa chưa độc lập. `started` là local của từng invocation; `elapsed.record` cần concurrency contract phù hợp. `@Trace` có thể quyết định **có đo hay không**, nhưng không cung cấp sẵn sự an toàn đồng thời cho code đo.

## 19. Annotation không tự lan qua thread hay Future

Giả sử một annotated method trả `CompletableFuture<Result>`. AOP advice ở **method execution boundary** có thể chạy khi method **trả Future**, trước khi công việc thực hoàn tất:

~~~text
Caller -> annotated start()
            -> returns Future immediately
                       |
                       v
                task completes later
~~~

Để ghi event khi task hoàn thành, interceptor phải gắn vào completion stage hoặc task lifecycle được hỗ trợ; method annotation không tự cung cấp context propagation/cancellation handling.

Tương tự, `ThreadLocal` chứa request context không tự lan sang mọi executor. Annotation trên method của caller cũng không khiến một remote worker có cùng call stack. Đừng xây bảo đảm audit/bảo mật phân tán trên giả định ấy.

## 20. Kiểm thử theo bốn chiều: declaration, discovery, matching, execution

Một test chỉ assert:

~~~java
assert method.isAnnotationPresent(Trace.class);
~~~

mới chứng minh **metadata tồn tại**, chưa chứng minh advice chạy. Một suite tốt chia:

| Chiều | Test cần có |
| --- | --- |
| Declaration | Annotation có target, retention, default values đúng |
| Discovery | Reader thấy direct annotation ở đúng method/class |
| Matching | Rule chọn annotated method; loại unannotated method |
| Execution | Caller qua interceptor nhận advice; raw call không bị “ảo tưởng” instrument |
| Nested/self-call | Self-invocation có được/không được chọn theo engine |
| Exception | Target throw, advice không che lỗi ngoài policy |
| Concurrent | Invocation-local context không lẫn |
| Async | Method return vs Future completion không bị nhầm |
| Versioning | Woven/proxy artifact đúng version và classloader |
| Security | Không bypass nơi enforcement bắt buộc |
| Negative coverage | Không vô tình instrument unrelated methods |
| Performance | Overhead phù hợp, metadata scan cache đúng |

### 20.1. Test matrix đề nghị

1. Gọi method annotated trên **raw target**: trong proxy-only setup, không có proxy advice.
2. Gọi cùng method qua **proxy**: advice chạy đúng số lần.
3. Gọi unannotated method qua proxy: chỉ business behavior.
4. Annotation chỉ trên interface method: kiểm đúng lookup policy.
5. Annotation chỉ trên implementation method: kiểm đúng lookup policy.
6. `SOURCE`/`CLASS` retention: reflection không thấy, nhưng build-time/classfile consumer có thể.
7. Composed/repeatable annotations: test trực tiếp và gián tiếp theo policy.
8. Method overloaded/generic/bridge: map method đúng, không match nhầm.
9. Inner self-invocation: xác nhận proxy/weaving behavior thực.
10. Nhiều aspects: order/precedence và exception propagation.
11. After-returning advice throw: caller outcome & side effects hợp đồng.
12. Target async complete exceptionally: completion observer đúng.
13. Plugin classloader/reload: metadata cache và class identity đúng.
14. Raw/woven artifact mismatch: build test báo lỗi rõ thay vì silently thiếu advice.
15. Security or audit annotation removed: fail behavior theo policy, không tự bỏ qua.

### 20.2. Test chính chương trình Java ở mục 6

Bài demo đã có assertions:
- `Trace` runtime visible.
- `BuildMark` và `SourceMark` không visible với reflection.
- `Feature("orders")` ở class được đọc đúng.
- Target chạy tổng cộng 3 lần.
- Proxy advice xuất hiện chỉ ở `run("B")`.
- `outer("C")` gọi nested `run` nhưng bypass proxy.

**Chỉ một test reflection `@Trace` true** sẽ bỏ lỡ sự khác biệt giữa `DIRECT` và `PROXY`. Đây chính là bài học trọng tâm.

## 21. Những nhầm lẫn phổ biến

1. **“Thêm `@Trace` tự in log.”** Sai nếu không có reader/matcher/interceptor.
2. **“Retention `RUNTIME` đồng nghĩa JVM tự áp aspect.”** Sai, nó chỉ hỗ trợ runtime metadata lookup.
3. **“Annotation processor là around advice.”** Không; processor chạy lúc compile, behavior runtime chỉ có nếu code/weaving được tạo tương ứng.
4. **“`CLASS` annotation luôn thấy qua `getAnnotation()`.”** Sai.
5. **“`SOURCE` annotation không thể có mục đích.”** Nó có thể dùng cho compile-time validation/generation.
6. **“`@Target(METHOD)` tạo method-execution join point.”** Không; target chỉ giới hạn vị trí annotation.
7. **“`@Target(FIELD)` tự intercept field reads.”** Không.
8. **“Annotation ở interface tự hiện trên implementation method.”** Không theo direct reflection thông thường.
9. **“`@Inherited` làm mọi method annotations được thừa kế.”** Không; quy tắc này không rộng như vậy.
10. **“Meta-annotation tự được mọi scanner nhận diện đệ quy.”** Không, scanner phải hỗ trợ traversal.
11. **“Một method có annotation nghĩa pointcut đã chọn nó.”** Chưa đủ: rule/capability/coverage vẫn quyết định.
12. **“Pointcut chọn được thì advice chắc chạy.”** Không nếu invocation bypass proxy hoặc class không woven.
13. **“Một proxy nhìn thấy mọi self-call.”** Không; `this.run()` có thể bypass.
14. **“Annotation ở class tự phủ mọi methods.”** Phải có rule xác định scope.
15. **“Annotation quyết định thứ tự của nhiều aspects.”** Không nếu engine cần precedence policy riêng.
16. **“Annotation auto rollback side effects.”** Không; transaction/compensation là cơ chế độc lập.
17. **“`@Cached` chứng minh method pure.”** Không.
18. **“Method return Future tức task đã thành công.”** Không.
19. **“Handler có `Method` nên call sẽ luôn match đúng overload.”** Cần map implementation/bridge/generics đúng.
20. **“Annotation metadata tự cung cấp thread-safety.”** Không.
21. **“Không cần test unannotated methods.”** Negative coverage rất quan trọng.
22. **“Test reflection true là integration test AOP.”** Không.
23. **“Thấy advice log là đã có security enforcement trên mọi đường.”** Không nếu raw/direct paths khác vẫn tồn tại.
24. **“Đổi annotation value không cần rebuild/redeploy.”** Phụ thuộc thời điểm scanning/weaving và cache.
25. **“Dynamic proxy làm thay đổi class target.”** Không; proxy có runtime class khác.
26. **“Annotation luôn là bắt buộc để viết pointcut.”** AOP có thể chọn bằng signature/flow/loại join point.
27. **“Cứ dùng reflection tại mỗi invocation là tối ưu.”** Có thể đắt, cần đo và cache đúng.
28. **“Annotation ở method cũng là annotation ở parameter/type use.”** Java phân biệt từng `ElementType`.
29. **“Một regex scan theo tên annotation là đủ an toàn.”** Có classloader/version/type identity và annotation values.
30. **“`@RequiresPermission` tự cấm caller không có quyền.”** Không có enforcement chỉ từ metadata.
31. **“Bỏ `@Trace` không ảnh hưởng gì nếu code target giữ nguyên.”** Nếu interceptor đọc annotation, behavior thực có thể thay đổi.
32. **“AOP engine nào cũng hỗ trợ constructors/private/fields.”** Join point capability khác nhau.
33. **“AOP runtime biết metadata nhưng không cần instance hook.”** Vẫn cần cơ chế cài can thiệp để advice chạy.
34. **“Một annotation ở interface đảm bảo classloader khác luôn hiểu nó.”** Runtime type identity và module visibility quan trọng.
35. **“Đã weave là đã gọi advice.”** Class definition/transform không đồng nghĩa một join point đã xảy ra.

## 22. Câu hỏi tự kiểm tra

**Câu 1.** Method có `@Trace` nhưng không có proxy, weaver hoặc interceptor. Có tự chạy advice không?  
**Đáp án:** Không; annotation chỉ khai báo metadata.

**Câu 2.** `RetentionPolicy.RUNTIME` thêm bảo đảm gì?  
**Đáp án:** Metadata có thể được đọc bằng Java runtime reflection API; nó không tự kích hoạt AOP.

**Câu 3.** `RetentionPolicy.CLASS` có thể hữu ích cho binary weaver không?  
**Đáp án:** Có nếu weaver đọc classfile attributes phù hợp; nhưng runtime reflection thông thường không thấy nó.

**Câu 4.** `@Target(ElementType.METHOD)` có nghĩa mọi calls tới method đều được intercept?  
**Đáp án:** Không. `@Target` chỉ nói vị trí được phép gắn annotation.

**Câu 5.** Annotation trên interface method có tự xuất hiện là direct annotation của method override không?  
**Đáp án:** Không; phải định nghĩa scanner traversal/lookup policy.

**Câu 6.** Vì sao `core.run("A")` không in advice dù `Core.run` có `@Trace`?  
**Đáp án:** Vì gọi raw core, không đi qua proxy interceptor.

**Câu 7.** Vì sao `proxy.run("B")` in advice?  
**Đáp án:** Vì invocation handler đọc metadata của implementation method, chọn true rồi chủ động in trước/sau.

**Câu 8.** Vì sao `proxy.outer("C")` không in advice cho `this.run("C")` bên trong?  
**Đáp án:** Self-invocation gọi trực tiếp method target, không đi qua proxy.

**Câu 9.** Có thể dùng annotation processor để tạo AOP runtime không?  
**Đáp án:** Có thể dùng processor sinh code phục vụ runtime; nhưng processor và annotation riêng lẻ không phải advice khi method chạy.

**Câu 10.** `@Inherited` làm method annotations tự được kế thừa?  
**Đáp án:** Không. Đó không phải quy tắc thừa kế tự động cho methods.

**Câu 11.** Với composed annotation, `isAnnotationPresent(Trace.class)` tự tìm transitive meta-annotations?  
**Đáp án:** Không; phải có traversal policy/cơ chế phù hợp.

**Câu 12.** Annotation `@Audited` bảo đảm event chỉ phát sau DB commit không?  
**Đáp án:** Không. Thời điểm commit và delivery cần được thiết kế ở transaction/outbox/advice boundary.

**Câu 13.** Làm sao chứng minh annotation-driven AOP thực sự hoạt động?  
**Đáp án:** Test đủ metadata, discovery, matching và invocation qua proxy/weaver thật, cả positive lẫn negative paths.

**Câu 14.** Vì sao reflection thấy `@Trace` chưa đủ kết luận mọi callers được bảo vệ?  
**Đáp án:** Vì callers có thể đi qua raw target, self-call, loader khác hoặc join points không instrument.

## 23. Checklist áp dụng thực tế

### Khai báo metadata

- [ ] Annotation có `@Target` phù hợp loại phần tử.
- [ ] Chọn `RetentionPolicy` theo consumer thực sự.
- [ ] Định nghĩa elements/default values và validation constraints rõ.
- [ ] Phân biệt annotation trên type/interface/implementation/method.
- [ ] Nếu dùng meta/repeatable annotations, xác định traversal policy.
- [ ] Tài liệu hóa rằng annotation tự nó không tạo AOP behavior.

### Cài cơ chế AOP

- [ ] Xác định method selector/pointcut cụ thể.
- [ ] Có cơ chế proxy/weaving/dispatcher được cài thật.
- [ ] Dùng đúng classloader/artifact; tránh raw/woven mismatch.
- [ ] Với proxy, callers giữ proxy reference, không raw target nếu cần interception.
- [ ] Resolve implementation vs interface annotation đúng.
- [ ] Xử lý overload, generics, bridge methods và Object methods.
- [ ] Xác định advice order, exception propagation và `proceed` contract.
- [ ] Không dùng annotation như bảo đảm auth/transaction/caching mặc định.
- [ ] Thiết kế policy khi annotation thiếu hoặc metadata invalid.
- [ ] Cache scanner metadata an toàn theo classloader/version.

### Kiểm thử và vận hành

- [ ] Reflection tests theo SOURCE/CLASS/RUNTIME retention.
- [ ] Direct annotated method không tự chạy advice trong môi trường chưa instrument.
- [ ] Proxy/woven invocation có chạy advice chính xác.
- [ ] Unannotated method không bị match nhầm.
- [ ] Test interface vs implementation placement và `@Inherited`.
- [ ] Test self-invocation/proxy bypass, nested calls và exception.
- [ ] Test async completion/context và concurrency khi concern yêu cầu.
- [ ] Kiểm coverage của field/constructor/private join points theo tool capability.
- [ ] Kiểm true runtime artifact/configuration, không chỉ gọi advice unit-test trực tiếp.
- [ ] Benchmark scanning/interception overhead và theo dõi failures khi triển khai.
- [ ] Audit annotation changes như thay đổi policy/API, không chỉ cosmetic code change.

## 24. Tổng kết

**Annotation là siêu dữ liệu; AOP là một cơ chế can thiệp vào việc thực thi chương trình.** `@Trace`, `@Audited` hay một annotation tùy biến chỉ khai báo dữ liệu. Chúng có ý nghĩa AOP **khi** một reader/pointcut rule nhận diện chúng **và** một proxy, weaver, transformer hoặc interceptor thật sự khiến advice chạy ở join point đã chọn. `@Retention(RUNTIME)` giúp reflection thấy annotation, chứ không tự khởi chạy advice.

Muốn thiết kế đúng, hãy tách **declaration → discovery → matching → interception → execution**, phân biệt interface/implementation metadata, retention, meta-annotations, proxy bypass, self-invocation và async boundaries. Với các concern bắt buộc như phân quyền hoặc audit, đừng coi annotation là ranh giới an toàn nếu chưa có enforcement và coverage tests tương ứng.

Ví dụ Java 17 chứng minh điều này một cách có thể kiểm tra: cùng method có `@Trace` nhưng raw call không in advice; proxy call in before/after vì handler được viết rõ ràng; unannotated method không match; internal `this.run()` không đi qua proxy. **Chìa khóa không phải dấu @, mà là cơ chế đọc và áp dụng quy tắc được xây dựng có chủ đích.**

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="annotation-metadata-003">Đánh dấu ở cấp kiểu, phương thức hoặc tham số</a>

<details>
<summary>Xem chi tiết</summary>

# Đánh dấu ở cấp kiểu, phương thức hoặc tham số trong AOP

## 1. Tại sao vị trí đánh dấu lại quan trọng?

Trong **Aspect-Oriented Programming (AOP)**, một **aspect** mô tả mối quan tâm xuyên cắt; một **pointcut** xác định tập **join point** được chọn, còn **advice** thực hiện hành vi bổ sung trước, sau hoặc bao quanh những join point đó. **Annotation** là một loại **metadata khai báo** thường giúp xác định ý định của tác giả mã nguồn thay vì chỉ phỏng đoán từ tên method hoặc package.

Ví dụ cùng ý tưởng audit có thể được đánh dấu ở ba vị trí:

~~~java
@AuditedType
class PaymentOperations {

    @AuditedOperation
    void transfer(@SensitiveValue String account, long amount) {
        // Mã xử lý chuyển tiền
    }
}
~~~

Đây là **snippet minh họa**, chưa phải một chương trình độc lập. Ba annotation ở đây chưa được định nghĩa; phần sau sẽ có ví dụ Java 17 đầy đủ.

**Ba mức đánh dấu diễn đạt ba ý nghĩa khác nhau:**

1. **Cấp kiểu (type):** mô tả vai trò, chính sách mặc định hoặc phạm vi cho toàn bộ class/interface/annotation type.
2. **Cấp phương thức (method):** mô tả một operation cụ thể: cần audit, retry, đo thời gian, kiểm soát quyền, validation hoặc tracing.
3. **Cấp tham số (parameter):** mô tả **một tham số khai báo**: dữ liệu nhạy cảm, định danh tenant, giá trị cần validate hoặc tham số dùng làm cache key.

Điều quan trọng: annotation chỉ là **metadata**. Bản thân việc đặt @AuditedOperation hoặc @SensitiveValue **không tự động chạy advice**, không tự tạo kiểm tra bảo mật và không tự lọc dữ liệu nhạy cảm. Cần có một compiler/weaver/interceptor/metadata processor thật sự hiểu và áp dụng quy tắc đó.

Bài tập trung **AOP tổng quát**, từ chọn join point bằng metadata đến đọc metadata runtime, precedence, inheritance, override, proxy, weaving, validation, concurrency và testing. Java 17 dùng để minh họa annotation/reflection một cách chạy được; các biểu thức pointcut ở phần giải thích là **giả mã khái niệm**, không giả định mọi ngôn ngữ AOP có cùng syntax.

## 2. Annotation khác comment, naming convention và marker interface

| Cách biểu đạt | Điểm mạnh | Giới hạn |
|---|---|---|
| Comment | Dễ diễn giải ý định | Không có semantics tự động với compiler/runtime nếu không có processor |
| Tên như saveAudited | Có thể đọc được | Mơ hồ, khó kiểm soát rename và overload |
| Marker interface | Biểu diễn **quan hệ kiểu** | Không mô tả tự nhiên một tham số hoặc một method riêng |
| Type annotation | Rõ chính sách của class/interface | Chưa nhất thiết diễn đạt mọi method, cần quy tắc kế thừa/phủ |
| Method annotation | Rõ operation cụ thể | Cần xác định method declaration nào được soi |
| Parameter annotation | Gắn metadata đúng tham số | Cần reflection/binding và cách áp dụng chính sách |
| External configuration | Không làm code chứa nhiều metadata | Mapping với type/method có thể drift theo refactor |

Một marker interface có thể thay type hierarchy; annotation thường mô tả metadata mà không nhất thiết đổi inheritance. Trong AOP, chọn theo annotation có thể sạch hơn chọn theo tên, nhưng **không có một cách tốt nhất cho mọi trường hợp**.

### 2.1. Annotation là khai báo; advice là hành động

~~~text
Source code
   |
   +-- Annotation metadata
   |
   v
Compiler / runtime metadata
   |
   v
Pointcut matching / metadata lookup
   |
   v
Advice selected?
   |
   +-- Không --> Target thực thi theo đường bình thường
   |
   +-- Có ---> Before/Around/After theo ngữ nghĩa engine
~~~

Nếu annotation không được retention hoặc nếu target không nằm trong phạm vi weaving, điểm đánh dấu có thể hoàn toàn không dẫn tới behavior mong đợi.

## 3. Các thành phần định nghĩa annotation trong Java

Một custom annotation là một kiểu đặc biệt khai báo bằng @interface.

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER})
@interface PolicyMark {
    String value();
}
~~~

Đây là một đoạn minh họa; có thể chạy khi đặt trong một source file hợp lệ cùng imports và declarations cần thiết.

Các thành phần:

- **@interface:** định nghĩa annotation type.
- **@Target:** hạn chế những vị trí khai báo nào được phép mang annotation.
- **@Retention:** chỉ định annotation còn tồn tại tới giai đoạn nào.
- **Annotation elements:** value(), name(), enabled()... mô tả dữ liệu được gắn.
- **Default value:** cung cấp mặc định cho element khi annotation không truyền giá trị đó.

### 3.1. @Target không đồng nghĩa type-checking của AOP

@Target giúp compiler kiểm tra vị trí **đặt annotation**. Nó không tự nói một AOP pointcut sẽ match các vị trí đó; việc match vẫn do ngôn ngữ AOP và join point model quyết định.

### 3.2. Annotation elements có giới hạn kiểu

Trong Java, annotation elements không thể tùy ý là Object, List hay một Strategy instance mutable. Những loại được phép gồm primitives, String, Class literals, enums, annotation types và mảng một chiều của các loại cho phép. Giá trị annotation phải đáp ứng quy tắc compile-time constant thích hợp.

Nếu chính sách cần cấu trúc dữ liệu phức tạp, hãy gắn một **policy ID hoặc enum** rồi resolve ra immutable Policy object trong một registry. Đừng biến annotation thành một ngôn ngữ cấu hình tùy tiện không validate.

## 4. Cấp kiểu: @Target(TYPE)

Annotation cấp kiểu có thể được đặt trên class, interface, enum, record hoặc annotation type tùy @Target và quy tắc ngôn ngữ.

~~~java
@AuditedType
class InventoryService {
    void receive() { }
    void ship() { }
}
~~~

Đây là snippet minh họa, chưa định nghĩa @AuditedType.

Cách diễn giải AOP có thể là: mọi operation **trong những type mang @AuditedType** là ứng viên cho audit advice, trừ khi policy loại trừ. Một rule khác có thể chỉ ghi metadata để registrar biết class có một capability.

### 4.1. Cấp kiểu là chính sách mặc định hay bắt buộc?

Cần phân biệt:

- **Mặc định:** annotation cấp kiểu áp dụng cho mọi method trừ khi có override hợp lệ.
- **Bắt buộc:** mọi method có effect quan trọng phải chịu policy, annotation method không được tắt.
- **Phạm vi:** chỉ những method có annotation riêng mới được chọn, nhưng type annotation xác định group/tenant.
- **Đăng ký:** chỉ ghi nhận type để một công cụ build/runtime scan, không tự chọn mọi method.

Không thể suy semantics từ tên @AuditedType; quy tắc thực phải được tài liệu hóa.

### 4.2. Cấp kiểu không phải khai báo tại mọi method

Một class có annotation **không làm annotation ấy xuất hiện trên các Method objects** của từng method trong Java reflection. Một tool có thể tự kết hợp method metadata với declaring/runtime type metadata để suy inherited/default policy, nhưng đó là **logic của tool**, không phải Java tự nhân bản annotation.

## 5. Cấp phương thức: @Target(METHOD)

Annotation cấp method diễn đạt chính xác **một operation**.

~~~java
class PaymentService {
    @Measured
    void charge() { }

    void lookup() { }
}
~~~

Đây là snippet minh họa; @Measured chưa được định nghĩa.

Cơ chế AOP có thể lựa chọn execution của charge() mà không áp vào lookup(). Nó hữu ích cho các chính sách:

- Theo dõi latency theo operation.
- Audit một thao tác đụng trạng thái quan trọng.
- Retry một operation idempotent có điều kiện.
- Authorization ở entrypoint tin cậy.
- Giới hạn tần suất xử lý.
- Transaction boundary theo yêu cầu.
- Phân loại event schema cho một API.

### 5.1. Method annotation không nhất thiết bằng call join point

Một AOP language có thể phân biệt:

- **call join point:** tại vị trí gọi một method.
- **execution join point:** khi method body thực thi.
- **constructor execution:** không phải METHOD annotation bình thường.
- **field get/set:** không phải method execution.

Nếu mục tiêu là **mọi lần method thực thi**, cần hiểu pointcut chọn execution hay call. Annotation nằm trên method declaration không tự quyết định join point kind.

### 5.2. Method annotation và overload

Hai overloads:

~~~java
void save(String id) { }
void save(String id, boolean force) { }
~~~

có thể mang annotations khác nhau. Match theo tên save đơn thuần dễ chọn sai. Khi cần phân biệt rõ method, xem tên, declaring type, parameter types, return signature và annotation của **đúng Method**.

### 5.3. Getter, bridge và compiler-generated methods

Compiler có thể tạo **bridge methods** cho generic override hoặc covariant return. Khi scan reflection và lựa chọn join points, có nguy cơ nhìn thấy bridge/synthetic methods với metadata khác nhau.

Nên kiểm tra Method.isBridge(), Method.isSynthetic() khi cần và xác định policy áp ở logical user method hay generated bridge. Không mặc định mọi runtime/compiler truyền annotation tới bridge theo cùng một chiến lược.

## 6. Cấp tham số: @Target(PARAMETER)

Annotation cấp tham số gắn với **parameter declaration**:

~~~java
void transfer(
    @SensitiveValue String accountId,
    @ValidatedAmount long amount,
    @CorrelationId String traceId
) { }
~~~

Đây là snippet minh họa. Các annotation cần được định nghĩa riêng.

Các use cases:

- **Redaction:** không log giá trị của tham số nhạy cảm.
- **Validation:** kiểm tra nonblank, range, format của một tham số.
- **Context extraction:** lấy tenant ID hoặc correlation ID từ vị trí đúng.
- **Cache key selection:** xác định tham số nào góp phần tạo key.
- **Metrics tags:** chỉ dùng whitelisted fields.
- **Authorization:** tham số nào biểu diễn resource ID; vẫn cần context tin cậy.
- **Data classification:** phân biệt PII/secrets và dữ liệu công khai.

**Parameter annotation không phải annotation của runtime value.** Nếu method có @SensitiveValue String token, metadata thuộc declaration của tham số, không tự gắn vào String object truyền vào.

### 6.1. Parameter index và parameter name

Reflection trả các Parameter objects tương ứng vị trí khai báo; có thể xem index và annotations. **Tên tham số** không nhất thiết được giữ dưới dạng đáng tin mặc định trong bytecode; để reflection thấy source names thường biên dịch với **javac -parameters**.

Không cần -parameters để đọc annotation được retain tại runtime; hai cơ chế độc lập. Một processor có thể dùng parameter position thay vì name khi xác định value tương ứng.

### 6.2. Parameter annotations và arrays

Java reflection có thể dùng:

~~~java
Parameter[] parameters = method.getParameters();
for (Parameter parameter : parameters) {
    // Đọc annotation khai báo trên từng parameter.
}
~~~

Hoặc getParameterAnnotations(). Nhưng cần bảo đảm method đã được giải quyết đúng, đặc biệt khi interface method, implementation method, bridge method hoặc proxy method có metadata khác nhau.

### 6.3. TYPE_USE không đồng nghĩa PARAMETER

@Target(PARAMETER) cho phép đánh dấu **declaration của tham số**. @Target(TYPE_USE) cho phép annotation ở **nơi dùng kiểu**, chẳng hạn trên generic type argument hoặc một vị trí type use trong khai báo.

~~~java
void handle(@SensitiveValue String token) { }
List<@NonNullType String> ids;
~~~

Đây là snippets minh họa; cần định nghĩa annotation types với targets phù hợp. Annotation @NonNullType ở generic type argument cần TYPE_USE, không phải chỉ PARAMETER.

Với type-use annotation, đọc metadata có thể phải dùng **AnnotatedType/AnnotatedParameterizedType** thay vì chỉ Method.getParameterAnnotations(). Các AOP languages không nhất thiết hỗ trợ mọi type-use annotation như điều kiện pointcut.

## 7. Retention: SOURCE, CLASS, RUNTIME

Java @Retention có ba giá trị chính:

| Retention | Có trong nguồn? | Có trong class file? | Reflection runtime thấy trực tiếp? |
|---|---|---|---|
| **SOURCE** | Có | Thường không lưu | Không |
| **CLASS** | Có | Có, theo class-file annotations | Thường không qua core reflection API |
| **RUNTIME** | Có | Có | Có, theo phạm vi reflection được hỗ trợ |

Điểm cần hiểu:

- **SOURCE** phù hợp cho source analyzers, annotation processors hoặc compiler checks.
- **CLASS** phù hợp cho binary tooling hoặc weaver đọc class file mà không cần reflection thấy runtime.
- **RUNTIME** cần thiết nếu dùng Java reflection runtime để đọc annotation qua getAnnotation/getAnnotationsByType.

### 7.1. Không phải AOP nào cũng cần RUNTIME

Một **compile-time weaver** có thể đọc metadata trong nguồn/class file để dệt advice và tạo bytecode cuối, không nhất thiết đòi reflection runtime thấy annotation. Ngược lại, một **runtime reflection-based interceptor** thường cần RUNTIME retention nếu nó đọc annotations qua core reflection.

Vì vậy chọn retention theo **lúc nào và bằng công cụ nào metadata được đọc**, không theo một lời khuyên chung “AOP luôn phải RUNTIME”.

### 7.2. @Retention mặc định

Nếu không ghi @Retention, Java annotation type mặc định có **CLASS retention**. Không tự giả định một annotation viết tay sẽ có thể đọc bằng getAnnotation ở runtime.

### 7.3. Không nhầm RUNTIME với runtime behavior

@Retention(RUNTIME) chỉ giữ metadata; không làm annotation tự gọi advice hoặc policy. Nếu không có một consumer xử lý nó, annotation không tạo hiệu ứng.

## 8. @Inherited chỉ hỗ trợ một loại kế thừa hẹp

**@Inherited** là meta-annotation mà Java reflection hỗ trợ cho **class-level annotation inheritance** trong những điều kiện được định nghĩa.

Ví dụ:

~~~java
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface Category { String value(); }

@Category("base")
class BaseTask { }

class DerivedTask extends BaseTask { }
~~~

Đây là ví dụ minh họa, cần imports.

Khi tìm Category trên DerivedTask.class, một truy vấn reflection phù hợp có thể nhìn thấy Category kế thừa từ superclass dù DerivedTask không khai báo trực tiếp.

Nhưng có ba giới hạn:

1. **Không tự kế thừa annotation của interface** sang implementing class theo Java @Inherited rules.
2. **Không áp dụng cho method annotation** chỉ vì subclass override method.
3. **Không áp dụng cho parameter annotations** của method overriding một method khác.

Một annotation type có @Inherited và đồng thời cho phép TYPE/METHOD/PARAMETER **không làm method/parameter annotations tự được inherited**.

### 8.1. Kế thừa metadata khác kế thừa behavior

Java inheritance có thể cho subclass sử dụng inherited method body. Nếu subclass không override method, reflection tìm method theo API phù hợp có thể trả inherited Method object mang annotation của superclass. Nhưng nếu subclass **override method không đánh dấu**, annotation trên override declaration không tự được truyền từ base method.

Hai trường hợp này khác nhau:

~~~text
Child không override -> gọi inherited Base.execute method.
Child override không annotate -> Child.execute là declaration mới.
~~~

### 8.2. Vì sao điều này nguy hiểm cho AOP?

Nếu một security rule chỉ kiểm tra annotation trực tiếp của concrete override, nó có thể bỏ sót annotation security trên base declaration. Nhưng nếu nó luôn lùi về base thì có thể áp một policy cũ mà subclass muốn thay đổi.

Không có luật mặc định chung; **quy tắc hợp nhất metadata qua hierarchy phải được xác định, test và quản trị**. Với enforcement bắt buộc, tránh suy “thiếu annotation tức allow”.

## 9. @Repeatable: nhiều rule trên cùng vị trí

Nếu một method cần nhiều nhãn độc lập:

~~~java
@Rule("audit")
@Rule("timed")
void transfer() { }
~~~

Annotation Rule phải khai báo **@Repeatable(Container.class)** và một container annotation phù hợp.

Trong Java, khi cần lấy **tất cả annotations lặp**, dùng:

~~~java
Rule[] rules = method.getAnnotationsByType(Rule.class);
~~~

thay vì chỉ dùng getAnnotation(Rule.class) và giả định nó trả toàn bộ.

### 9.1. Container annotation không chỉ là chi tiết cú pháp

Compiler biểu diễn repeated annotations bằng một container trong các trường hợp cần thiết. Container cần target/retention tương thích; nếu muốn class-level inheritance của repeated annotations, cần chú ý @Inherited trên repeatable type và container theo Java rules.

### 9.2. Precedence khi có nhiều rules

Một method @Rule("audit") @Rule("timed") không xác định sẵn advice ordering. Có thể có nhiều aspects độc lập, hoặc một consumer tổng hợp rules rồi áp một strategy. Thứ tự advice phải đến từ AOP semantics/precedence tường minh, không lấy thứ tự khai báo annotations làm hợp đồng nếu công cụ không bảo đảm.

## 10. Ví dụ Java 17 chạy được: TYPE, METHOD, PARAMETER, @Inherited và @Repeatable

Ví dụ này có:

- Annotation @Rule dùng được ở TYPE, METHOD và PARAMETER.
- @Retention(RUNTIME) để reflection nhìn thấy.
- @Inherited để kiểm chứng **class-level inheritance**.
- @Repeatable(Rules.class) để gắn hai rules vào một method.
- Interface được annotate nhưng implementing class không tự inherited.
- Base method được annotate; Child override không tự inherited.
- Parameter annotations của Base không tự xuất hiện trên Child override.
- Compiler flag **-parameters** để in ra source parameter names.

**Đây là reflection demo, không tự dệt AOP advice.** Output giúp xác định metadata mà một AOP integration có thể cần đọc trước khi thực thi policy.

Tạo file **AnnotationScopeDemo.java**:

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.stream.Collectors;

public class AnnotationScopeDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER})
    @Inherited
    @Repeatable(Rules.class)
    @interface Rule {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER})
    @Inherited
    @interface Rules {
        Rule[] value();
    }

    @Rule("type:parent")
    static class Parent {
        @Rule("method:audit")
        @Rule("method:timed")
        public String execute(@Rule("param:redact") String secret,
                              @Rule("param:positive") int count) {
            return "parent:" + count;
        }
    }

    static class Child extends Parent {
        @Override
        public String execute(String secret, int count) {
            return "child:" + count;
        }
    }

    @Rule("type:interface")
    interface Contract {}

    static class ContractImpl implements Contract {}

    static String labels(AnnotatedElement element) {
        return Arrays.stream(element.getAnnotationsByType(Rule.class))
                .map(Rule::value)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    static String parameterNames(Method method) {
        return Arrays.stream(method.getParameters())
                .map(Parameter::getName)
                .collect(Collectors.joining(","));
    }

    public static void main(String[] args) throws Exception {
        Method parentMethod = Parent.class.getDeclaredMethod(
                "execute", String.class, int.class);
        Method childMethod = Child.class.getDeclaredMethod(
                "execute", String.class, int.class);
        System.out.println("Parent.type=" + labels(Parent.class));
        System.out.println("Child.type=" + labels(Child.class));
        System.out.println("Child.declared=" + Arrays.toString(
                Child.class.getDeclaredAnnotationsByType(Rule.class)));
        System.out.println("ContractImpl.type=" + labels(ContractImpl.class));
        System.out.println("Parent.method=" + labels(parentMethod));
        System.out.println("Child.method=" + labels(childMethod));
        System.out.println("Parent.param0=" + labels(parentMethod.getParameters()[0]));
        System.out.println("Parent.param1=" + labels(parentMethod.getParameters()[1]));
        System.out.println("Child.param0=" + labels(childMethod.getParameters()[0]));
        System.out.println("Parent.parameterNames=" + parameterNames(parentMethod));
        System.out.println("Child.execute=" + new Child().execute("hidden", 2));
    }
}
~~~

Biên dịch và chạy trên JDK 17:

~~~bash
javac --release 17 -parameters AnnotationScopeDemo.java
java AnnotationScopeDemo
~~~

**Stdout chính xác đã được kiểm chứng:**

~~~text
Parent.type=[type:parent]
Child.type=[type:parent]
Child.declared=[]
ContractImpl.type=[]
Parent.method=[method:audit, method:timed]
Child.method=[]
Parent.param0=[param:redact]
Parent.param1=[param:positive]
Child.param0=[]
Parent.parameterNames=secret,count
Child.execute=child:2
~~~

### 10.1. Vì sao Child.type có type:parent?

Rule và Rules cùng mang @Inherited. Parent có @Rule("type:parent"), Child kế thừa Parent, nên truy vấn getAnnotationsByType trên **Child.class** nhìn thấy annotation inherited trong điều kiện demo.

### 10.2. Vì sao Child.declared rỗng?

getDeclaredAnnotationsByType chỉ đọc annotations **khai báo trực tiếp** trên Child, không lấy annotations inherited. Vì Child không annotate riêng nên kết quả [].

### 10.3. Vì sao ContractImpl.type rỗng?

Contract interface có @Rule("type:interface"), nhưng @Inherited không truyền type annotation từ **interface** sang một class implements nó. Một AOP consumer muốn lấy annotations trên interfaces cần **tự chủ động duyệt interfaces** và có chính sách xử lý conflict.

### 10.4. Vì sao Parent.method có hai rules?

Parent.execute mang hai @Rule và annotation repeatable, nên getAnnotationsByType trả cả hai theo reflection semantics.

### 10.5. Vì sao Child.method rỗng?

Child tự override execute, không khai báo @Rule trên method override. Java reflection không tự sao chép method annotations từ Parent.execute sang Child.execute. Tương tự, annotation trên parameter 0 không tự xuất hiện ở parameter declaration của Child.execute.

### 10.6. Vì sao Parent.parameterNames có secret,count?

Demo biên dịch với **-parameters**, cho phép reflection nhìn tên source parameters trong class file. Không có flag này, tên có thể là arg0/arg1; metadata annotation vẫn được retain bởi @Retention(RUNTIME), độc lập với parameter names.

### 10.7. Vì sao Child.execute là child:2?

Đây chỉ là kết quả của Java overriding và method invocation. Annotation @Rule trên Parent.method không làm code của Child tự gọi logging, audit hoặc validation. Một AOP engine cần được cấu hình để hành động.

## 11. Cách sử dụng annotation làm pointcut

Một thiết kế AOP có thể có những điều kiện khái niệm:

~~~text
Candidate executions within types marked @AuditedType
    AND/OR
Executions of methods marked @AuditedOperation
    AND/OR
Method execution whose parameter declaration carries @SensitiveValue
~~~

**Đây là giả mã**, không phải cú pháp pointcut dùng chung.

### 11.1. Chọn theo type

Nếu aspect quan tâm mọi public operations trong nhóm types có @Observed, một type-based pointcut có thể xác định phạm vi chung. Nhưng phải làm rõ annotation được đọc từ:

- Declaring type của method.
- Runtime receiver class.
- Parent superclass.
- Interface declaration.
- Proxy/generated subclass.
- Một metadata index được build trước.

Những lựa chọn này **không đồng nhất** khi có inheritance/overrides/proxies.

### 11.2. Chọn theo method

Một method-annotation pointcut có thể đánh dấu một execution đặc biệt. Nếu annotation nằm ở **interface method**, implementation method không annotate, matcher cần biết nó đọc interface declaration hay concrete implementation.

Một interception model dựa proxy có thể nhìn **interface Method object**; một execution weaver có thể chọn **concrete implementation method**. Không tuyên bố một annotation trên interface tự luôn match execution của implementation ở mọi engine.

### 11.3. Chọn theo parameter

Không phải mọi AOP engine có sẵn pointcut primitive “method với tham số thứ hai có @SensitiveValue”. Nếu không hỗ trợ trực tiếp, có thể:

1. Chọn một tập method executions đủ hẹp.
2. Resolve metadata trên **đúng Method declaration**.
3. Đọc annotations từng Parameter theo index.
4. Ghép với arguments của invocation theo contract.
5. Dùng policy xác định argument nào được phép quan sát/log/validate.
6. Thực thi behavior trong advice theo lifecycle đúng.

Phải xử lý varargs, bridge methods, overloaded methods và proxy interface signatures nếu hệ thống sử dụng chúng.

## 12. Kết hợp ba cấp annotation thành một effective policy

Trong thiết kế thực tế, một method có thể nhận chính sách từ type, method và parameter cùng lúc.

Ví dụ:

~~~text
Type:    @Audit(enabled=true, mode=SUMMARY)
Method:  @Audit(mode=FULL)
Param 0: @Sensitive
Param 1: @PublicField
~~~

Mong muốn:

- Type định nghĩa default audit.
- Method có thể override mode theo quy tắc.
- Parameter annotations giới hạn **fields được phép ghi**.

Một **thứ tự hợp nhất metadata** có thể là:

~~~text
Global mandatory constraints
        |
        v
Type-level defaults
        |
        v
Method-level specialization
        |
        v
Parameter-level data classification
        |
        v
Effective policy validated
        |
        v
Advice execution
~~~

Đây là **một lựa chọn kiến trúc**, không phải quy tắc kế thừa annotation mặc định của Java hay tất cả ngôn ngữ AOP.

### 12.1. Phải phân biệt override được phép và invariant bắt buộc

Ví dụ type @AuditRequired có thể quy định mọi operation phải audit. Một method-level annotation @NoAudit không được phép vô hiệu hóa yêu cầu bắt buộc chỉ vì nó “ở cấp cụ thể hơn”.

Ngược lại, type @ObserveDefault có thể được method @IgnoreMetric override. Cần mô hình hóa rõ **non-overridable mandatory policy** và **overridable default policy**.

### 12.2. Không dùng policy merge ngầm

Khi hai interfaces cùng khai báo method với annotations khác nhau, một implementation có thể nhận hai conflicting policies nếu matcher duyệt cả hai. Phải có quy tắc conflict: báo lỗi build, chọn precedence đã công bố hoặc gộp theo một operation xác định. Không để thứ tự reflection iteration quyết định quyền truy cập.

### 12.3. Data classification là giới hạn trên việc quan sát

Nếu parameter @Sensitive, một annotation cấp method yêu cầu "log full arguments" không được tùy tiện ghi raw secret. Redaction/PII constraints có thể là quy tắc ưu tiên cao hơn, không phải option dễ bị override.

## 13. Reflection API quan trọng

| API | Công dụng |
|---|---|
| Class.getAnnotation(X.class) | Tìm annotation type theo semantics inherited của Java reflection |
| Class.getDeclaredAnnotation(X.class) | Chỉ annotation khai báo trực tiếp |
| AnnotatedElement.getAnnotationsByType(X.class) | Lấy các repeatable annotations, gồm semantics liên quan |
| AnnotatedElement.getDeclaredAnnotationsByType(X.class) | Lấy repeatable annotations khai báo trực tiếp |
| Method.getParameters() | Lấy Parameter objects |
| Method.getParameterAnnotations() | Lấy mảng annotation cho từng vị trí parameter |
| Parameter.getAnnotation(X.class) | Annotation trên parameter declaration |
| Parameter.getName() | Tên parameter nếu metadata được lưu |
| Method.isBridge() | Nhận diện compiler bridge method |
| Method.isSynthetic() | Nhận diện synthetic method |
| Class.getDeclaredMethods() | Methods khai báo trên class |
| Class.getMethods() | Public methods theo contract bao gồm inherited methods |

### 13.1. Annotation scanning cần cache có kiểm soát

Nếu lời gọi AOP chạy rất thường xuyên, tìm reflection metadata trên mọi invocation có thể tốn CPU và allocation. Có thể resolve **immutable policy descriptor** cho từng logical method rồi cache.

Nhưng key của cache cần tính tới:

- Declaring class/method signature.
- Class loader identity.
- Proxy/target mapping.
- Bridge/synthetic method resolution.
- Phiên bản policy/config.
- Reload/instrumentation lifecycle.

Không cache theo method name đơn thuần vì overloads khác nhau. Không dùng một global strong-reference cache vô hạn để giữ mọi ClassLoader plugin sống mãi.

### 13.2. Khi metadata thay đổi ở runtime?

Java annotations trên class definitions nhìn chung không phải một map mutable để bật/tắt tùy ý. Nếu cần thay policy runtime, dùng một **versioned immutable policy registry** dựa trên annotation IDs hoặc operation IDs thay vì sửa metadata giả.

## 14. Interface và implementation: xác định vị trí annotation đúng

Giả sử:

~~~java
interface Repository {
    @Traced
    void store(String key);
}

final class RepositoryImpl implements Repository {
    @Override
    public void store(String key) { }
}
~~~

Đây là snippets minh họa; @Traced chưa định nghĩa.

Một proxy interface-based có thể intercept thông qua Repository.store Method, còn implementation class method không có @Traced. Một direct execution matcher có thể nhìn RepositoryImpl.store và không tìm thấy annotation trực tiếp.

**Cùng một operation nhưng metadata sources khác nhau.** Thiết kế phải nêu một lựa chọn:

1. Annotation đặt trên interface là hợp đồng và được resolver duyệt chủ động.
2. Annotation bắt buộc đặt trên implementation để execution pointcut match.
3. Cả hai được hỗ trợ, có merge rule cụ thể.
4. Một lớp build-time validation đảm bảo interface và implementation đồng nhất.

Không khẳng định mọi runtime tự copy annotations từ interface sang implementation.

## 15. Kế thừa và override: những trường hợp đặc biệt

Nếu Parent.method có annotation và Child không override, một call tới inherited method có thể dùng Parent declaration, nên metadata trên Parent.method vẫn là metadata của method đang thực thi.

Nếu Child override không có annotation, metadata trên Child declaration khác Parent. Resolver nào chỉ đọc concrete method sẽ thấy rỗng.

Nếu Child override có một annotation khác, cần quyết định:

- Child annotation thay hoàn toàn Parent policy?
- Merge hai policies?
- Parent có một mandatory invariant?
- Child có được opt-out?
- Nếu override generic bridge phát sinh, Method object nào là logical declaration?

Với security policy, default behavior thiếu annotation cần được chọn có chủ đích và thường không thể là “cho qua” nếu thao tác cần enforcement.

## 16. Parameter annotation và redaction trong logging advice

Giả sử operation:

~~~java
void authenticate(
    @SensitiveValue String password,
    @PublicField String clientVersion
) { }
~~~

Đây là snippet minh họa.

Một advice log arguments không nên gọi:

~~~text
log("args=" + Arrays.toString(args))
~~~

vì nó có thể lộ password.

Giải pháp:

1. Resolve correct method metadata.
2. Tạo chính sách phân loại arguments bằng **parameter indices**.
3. Với parameter được đánh dấu sensitive, thay value bằng placeholder cố định.
4. Chỉ serialize fields có quyền được ghi, trong size/time limits.
5. Không để logger failure che business exception ngoài ý muốn.
6. Không in raw Throwable message/stack trace chứa secret nếu policy cấm.

### 16.1. Fail-closed đối với dữ liệu nhạy cảm

Nếu method metadata không truy cập được, tên parameter vắng mặt hoặc resolver tìm nhầm Method, với logging có dữ liệu nhạy cảm thường nên **không log arguments** thay vì log tất cả. Đây là lựa chọn conservative cần được ghi trong policy.

### 16.2. Annotation của parameter không kiểm tra giá trị tự động

Gắn @PositiveAmount vào một parameter không khiến Java compiler hoặc JVM mặc định từ chối -5. Một validator/advice phải có logic kiểm tra và quyết định ném exception ở boundary phù hợp.

### 16.3. Đừng chỉ tin metadata để bảo mật

Annotation @TenantId trên một parameter chỉ xác định **tham số nào được gọi là tenant ID**; nó không chứng minh giá trị tenant mà caller gửi thuộc quyền actor. Cần authorization dựa trên authenticated context và resource state.

## 17. Annotation và quyền áp dụng advice

Annotation nên được xem là **một tiêu chí selection**, không phải nguồn quyền lực tuyệt đối.

Ví dụ một method cần authorization nhưng developer quên gắn @RequiresPermission. Nếu bảo vệ chỉ dựa vào annotation này, method có thể được gọi mà không kiểm tra.

Cách giảm rủi ro:

- Áp mandatory security checks ở một boundary đáng tin.
- Có build-time checks cho operation bắt buộc phải annotate.
- Test positive và negative pointcut coverage.
- Dùng deny-by-default cho những operation class không có policy.
- Tách audit/telemetry optional khỏi authorization bắt buộc.
- Dùng static annotations như một lớp mô tả rõ, không thay threat model.

### 17.1. Annotation có thể bị đặt sai vị trí

@RequiresPermission trên một helper private không tương đương đặt nó ở API entrypoint nếu interceptor chỉ quan sát public interface calls. Weaver/proxy scope phải được xác minh.

### 17.2. Self-invocation và direct target access

Trong một số proxy-based mechanisms, lời gọi từ method này tới method khác **trên chính target** hoặc thông qua direct target reference có thể không đi qua proxy. Annotation vẫn tồn tại trên method nhưng advice không chạy. Binary weaving ở execution join point có khả năng khác.

Không dùng một annotation như chứng cứ rằng behavior luôn được enforce khi có các đường bypass.

## 18. Annotation inheritance không giải quyết aspect precedence

Giả sử type annotation yêu cầu tracing, method annotation yêu cầu transaction, parameter annotation yêu cầu redaction. Metadata này không mặc nhiên xác định:

~~~text
Tracing(Transaction(Business))
~~~

hay:

~~~text
Transaction(Tracing(Business))
~~~

Thứ tự outer/inner advice phụ thuộc precedence rules và configuration của engine. Khi transaction commit throw, tracing outer có thể thấy commit failure; tracing inner có thể đã kết thúc trước commit.

Một annotation có thể mang một field priority, nhưng **priority của policy** và **precedence thực thi** chỉ trùng nhau nếu engine được thiết kế để áp dụng đúng field đó.

### 18.1. Multiple annotations trên method

@Audit + @Retry + @Timed không tự nêu thứ tự. Cần có tests cho:

- Số lần business proceed.
- Số lượng audit events.
- Thời điểm đo start/stop.
- Exceptions gốc và cleanup errors.
- Transaction boundary.
- Khi policy chỉ match một số operations.

Không suy mọi annotation hoạt động độc lập; những advice lồng nhau có thể thay đổi kết quả quan sát.

## 19. Retry annotation cần kiểm soát idempotency

Gắn @Retryable lên method không tự khiến method an toàn khi chạy nhiều lần. Nếu method tạo side effects (gửi email, trừ tiền, publish message), một lần fail có thể xảy ra **sau khi side effect đã hoàn tất**.

Retry policy phải xem xét:

- Exception nào được retry.
- Maximum attempts.
- Backoff/jitter.
- Deadline/cancellation.
- Idempotency keys.
- Transaction per attempt.
- External side effect compensation.
- Mối quan hệ với logging/audit advice.

Đừng dùng annotation như lời hứa “method có thể chạy lại vô hại” nếu nghiệp vụ chưa xác nhận điều đó.

## 20. Async, threads và context propagation

Method annotation có thể ở method trả CompletableFuture hoặc một asynchronous handle.

~~~text
@Timed
CompletionStage<Result> fetchAsync(...)
~~~

Một after-returning advice trên method có thể chạy ngay sau khi method trả handle, **trước khi task thật hoàn tất**. Nếu policy muốn đo actual completion, cần instrument completion boundary.

Annotation cấp parameter như @CorrelationId có thể cho biết tham số nào mang correlation ID, nhưng **không làm ThreadLocal tự truyền qua Executor worker**.

Cần:

- Capture immutable context hợp lệ.
- Explicit propagation qua task/RPC khi được phép.
- Restore worker thread context trong finally.
- Không giữ thread-bound resource sai lifetime.
- Xác định khi nào log success/failure/cancellation.
- Kiểm tra task retry có cùng logical correlation nhưng attempt ID khác nhau không.

## 21. Kết hợp annotations với cấu hình runtime

Một annotation như @Policy("PAYMENT_AUDIT") có thể chỉ giữ một policy ID. Runtime registry map ID sang một immutable policy snapshot.

Lợi ích:

- Source thể hiện ý định.
- Policy chi tiết đổi qua validated configuration.
- Không cần code recompile mỗi khi đổi threshold.
- Có thể gắn version để audit.
- Giữ metadata nhỏ và type-safe hơn một annotation có hàng chục flags.

Rủi ro:

- Annotation trỏ policy ID không tồn tại.
- Runtime registry lỗi hoặc policy reload giữa invocation.
- Hai nodes có version khác nhau.
- Caller có thể cố chọn policy ID bất hợp lệ nếu API cho phép override.
- Metadata build-time chưa validate mapping.

Một invocation nên capture **một policy snapshot nhất quán** nếu before/after/failure cần cùng rule version. Với security/audit bắt buộc, missing policy behavior cần được định nghĩa và kiểm thử.

## 22. Annotation processing so với runtime AOP

Có hai giai đoạn khác nhau:

### 22.1. Build-time validation/processing

Một annotation processor hoặc compiler tool có thể:

- Phát hiện method thiếu annotation bắt buộc.
- Kiểm tra @SensitiveValue chỉ đặt trên những tham số hợp lệ.
- Sinh metadata index.
- Kiểm tra một annotation ID có được cấu hình không.
- Báo lỗi khi các annotations xung đột.
- Tránh một số lỗi trước khi deploy.

Nhưng một processor chỉ chạy lúc build **không mặc nhiên intercept mọi method runtime**.

### 22.2. Weaving hoặc runtime interception

Một AOP weaver/interceptor dùng metadata để chọn join points và thực thi advice. Nó có thể làm ở compile time, sau compile, lúc load hoặc qua proxy/runtime mechanism, tùy công nghệ.

Một chương trình có annotation processor nhưng không có weaving/interception phù hợp **không tự chạy AOP advice**.

## 23. Chi phí của annotation-based matching

Chi phí có thể xuất hiện ở:

- Scan type/method/parameter metadata lúc khởi tạo.
- Reflection resolution và class loader lookup.
- Resolve implementation Method từ interface Method.
- Caching policy descriptor.
- Runtime checks với dynamic context.
- Advice body: logging, metrics, serializer, remote policy.
- Allocation và GC của event/context objects.

### 23.1. Static matching có thể giảm overhead

Một compile-time weaver có thể quyết định nhiều join points bằng metadata sớm, rồi chỉ dệt những nơi match. Một runtime proxy có thể kiểm tra metadata và cache để giảm reflection work.

Không khẳng định mọi annotation-based AOP đều reflection-scan ở mỗi call hoặc luôn zero-cost.

### 23.2. Cache cần key đúng

Method signature bao gồm tên và parameter types; chỉ cache theo method name làm hai overloads bị trộn. Với interface/implementation mismatch, key có thể cần resolve logical operation identity theo cả concrete class và invoked method.

ClassLoader-scoped caches có thể gây memory retention nếu giữ Class/Method references bằng strong refs không giới hạn. Cần lifecycle hoặc weak-reference policy khi hỗ trợ plugins/reload.

### 23.3. Đo bằng workload thực

Khi tối ưu, phân biệt cost của metadata lookup với cost của advice body. Đừng đo một logger có System.out rồi quy toàn bộ latency cho annotation reflection. Có thể benchmark với JMH và kiểm tra p95/p99 qua profiling.

## 24. Ma trận kiểm thử annotation theo vị trí

| Kịch bản | Điều cần xác minh |
|---|---|
| Annotation ở class | Type-level selection đúng |
| Annotation ở interface | Resolver có hỗ trợ interface policy không |
| Subclass không annotate | @Inherited áp class annotation theo rule |
| Subclass override method | Method annotation không tự inherited |
| Override parameter | Parameter annotation không tự copy |
| Method overload | Đúng declaration được match |
| Generic bridge/synthetic method | Chọn logical method theo policy |
| Repeatable annotations | getAnnotationsByType trả đủ các rules |
| Annotation @Retention(SOURCE) | Runtime reflection không thấy |
| Annotation @Retention(CLASS) | Core reflection không mặc định thấy |
| Annotation @Retention(RUNTIME) | Reflection thấy khi query đúng |
| Parameter names không có -parameters | Resolver vẫn dựa index hợp lệ |
| @Target(TYPE_USE) | Đọc qua AnnotatedType nếu cần |
| Proxy interface vs target method | Metadata resolution đúng |
| Direct target/self-invocation | Không bypass enforcement ngoài dự kiến |
| Type + method policy | Precedence/merge được kiểm chứng |
| Parameter redaction | Không log raw secret |
| Missing mandatory policy | Fail-closed theo contract |
| Async handle return | Không nhầm schedule với task completion |
| Multiple aspects | Advice precedence đúng |
| Repeatable conflict | Resolver báo lỗi hoặc merge theo rule |
| Negative target | Method không được annotate không bị match nhầm |
| Class loader thay đổi | Reflection/cache không nhầm type |
| Policy reload | Một invocation có version nhất quán |

### 24.1. Test negative paths

Chỉ test một @AuditedOperation đã phát event là chưa đủ. Phải test các methods **không nên** phát event, hoặc không được phép tránh enforcement, để ngăn pointcut quá rộng/quá hẹp.

### 24.2. Fault injection

Chủ động gây lỗi trong target, trước proceed, sau proceed, logging sink, policy lookup và context cleanup. Xác nhận exception gốc còn giữ, resource context được restore và sensitive arguments không lộ ở tất cả các đường.

## 25. Những lỗi thường gặp

**Lỗi 1 — Annotation tự chạy logic.** Không, phải có consumer/weaver.

**Lỗi 2 — @Target(TYPE) có nghĩa tự annotate mọi Method.** Không; class metadata khác method metadata.

**Lỗi 3 — @Retention(RUNTIME) tự bảo đảm advice chạy.** Chỉ bảo đảm metadata runtime accessible theo API phù hợp.

**Lỗi 4 — Annotation default retention là RUNTIME.** Java default là CLASS.

**Lỗi 5 — @Inherited truyền annotation interface sang implementation.** Không theo Java reflection rules.

**Lỗi 6 — @Inherited truyền method annotation qua override.** Không.

**Lỗi 7 — @Inherited truyền parameter annotation qua override.** Không.

**Lỗi 8 — Đặt @SensitiveValue lên parameter tự che dữ liệu khi log.** Chỉ metadata, cần advice redaction.

**Lỗi 9 — PARAMETER và TYPE_USE là như nhau.** Không; type-use annotations có API metadata riêng.

**Lỗi 10 — getAnnotation luôn trả đủ repeated annotations.** Dùng getAnnotationsByType khi cần tất cả.

**Lỗi 11 — -parameters là bắt buộc để reflection đọc parameter annotations.** Không, nó chủ yếu liên quan source parameter names.

**Lỗi 12 — Annotation ở interface method luôn xuất hiện trên concrete override.** Không mặc định.

**Lỗi 13 — Một class @Audited cho biết transaction đã commit.** Không; phải phân biệt boundary và outcome.

**Lỗi 14 — Method @Retryable chứng minh idempotency.** Không.

**Lỗi 15 — Một method name là cache key đủ.** Overloads và class loaders có thể trùng tên.

**Lỗi 16 — Proxy và target có cùng Method object/type metadata.** Có thể khác nhau.

**Lỗi 17 — Runtime parameter annotation đi theo argument object.** Nó thuộc khai báo tham số, không phải object.

**Lỗi 18 — Multiple annotation order là advice precedence.** Không trừ khi ngôn ngữ/tool nói vậy.

**Lỗi 19 — Policy từ annotation luôn được tin cậy cho authorization.** Không thay authenticated actor/resource checks.

**Lỗi 20 — Reflection scan trên mỗi call không đáng quan tâm.** Có thể tốn kém ở hot paths; đo và cache hợp lý.

**Lỗi 21 — Thay config thì annotations trên class đang load tự thay.** Không.

**Lỗi 22 — Không annotation nghĩa luôn allow.** Điều này nguy hiểm cho enforcement bắt buộc.

## 26. Quy trình thiết kế annotation-driven aspect

1. **Xác định concern:** audit, trace, metrics, validation, security hay retry.
2. **Chọn vị trí đánh dấu:** TYPE cho group/default, METHOD cho operation, PARAMETER cho field-specific policy.
3. **Khai báo @Target chính xác**, không mở rộng tùy tiện.
4. **Chọn @Retention phù hợp** với compile-time weaver, class-file scanner hay runtime reflection.
5. **Quyết định @Inherited/@Repeatable** có thực sự cần không.
6. **Định nghĩa metadata semantics** thay vì chỉ đặt tên annotation.
7. **Định nghĩa chính sách hợp nhất** type/method/parameter và policy precedence.
8. **Phân biệt bắt buộc với có thể override**, đặc biệt security và redaction.
9. **Xác định điểm match**: call/execution, interface/concrete implementation, overload/bridge.
10. **Xác định cách truyền argument** theo đúng indices và data classification.
11. **Thiết kế exception/finally/async semantics** của advice.
12. **Xây metadata resolver có cache** với key và lifecycle đúng.
13. **Validate policy IDs và cấu hình** tại build/deploy boundary.
14. **Viết positive và negative tests** cho inheritance/override/interface/proxy.
15. **Kiểm tra privacy/security** ở cả success, error và logs.
16. **Đo hiệu năng** nếu advice nằm trên hot path.
17. **Tài liệu hóa limitation** của engine để tránh người dùng hiểu nhầm annotation làm magic.

## 27. Câu hỏi ôn tập có đáp án

**Câu 1.** Vì sao annotation trên class không đồng nghĩa annotation trên từng Method?

**Đáp:** Metadata class và method được khai báo ở các elements khác nhau; tool có thể tự hợp nhất theo policy nhưng Java không copy tự động.

**Câu 2.** @Target(PARAMETER) có bắt buộc validator chạy trước method không?

**Đáp:** Không; chỉ giới hạn nơi annotation được đặt.

**Câu 3.** Runtime reflection muốn đọc custom annotation thường cần retention nào?

**Đáp:** RUNTIME.

**Câu 4.** Một compile-time weaver có nhất thiết cần RUNTIME retention không?

**Đáp:** Không; nó có thể dùng source/class-file metadata tùy công cụ.

**Câu 5.** Java annotation không ghi @Retention mặc định là gì?

**Đáp:** CLASS.

**Câu 6.** @Inherited có áp cho overriding method không?

**Đáp:** Không theo Java annotation inheritance semantics.

**Câu 7.** Child extends Parent được @Inherited type annotation; getDeclaredAnnotationsByType trên Child trả inherited value không?

**Đáp:** Không; nó chỉ xét annotations khai báo trực tiếp.

**Câu 8.** Class implements annotated interface có tự inherited annotation type từ interface không?

**Đáp:** Không theo @Inherited; muốn hỗ trợ cần tự duyệt interface hoặc cơ chế riêng.

**Câu 9.** Vì sao dùng getAnnotationsByType với repeatable annotation?

**Đáp:** Để lấy đủ các annotation instances thay vì chỉ một biểu diễn.

**Câu 10.** Không biên dịch -parameters thì có mất parameter annotations runtime không?

**Đáp:** Không do flag đó; flag ảnh hưởng tên tham số, còn retention quyết định visibility của annotations.

**Câu 11.** Parameter annotation nằm trên String object truyền vào method không?

**Đáp:** Không, nó nằm trên parameter declaration.

**Câu 12.** TYPE_USE khác PARAMETER ở đâu?

**Đáp:** TYPE_USE đánh dấu một vị trí dùng kiểu; PARAMETER đánh dấu declaration của tham số.

**Câu 13.** Annotation trên interface method có chắc match concrete execution không?

**Đáp:** Không tự bảo đảm; phụ thuộc method metadata resolver và engine.

**Câu 14.** Vì sao không cache metadata theo method name đơn thuần?

**Đáp:** Overloads, declaring types và ClassLoaders có thể khác nhau.

**Câu 15.** @SensitiveValue có đủ để không lộ secret trong logs không?

**Đáp:** Không; logging advice phải chủ động redact và xử lý lỗi/diagnostics đúng.

**Câu 16.** Một method @Retryable có thể an toàn chạy lại sau external side effect không?

**Đáp:** Chỉ nếu retry policy và idempotency/compensation thật sự bảo đảm, không phải nhờ annotation.

**Câu 17.** Type @AuditRequired và method @DisableAudit xung đột, có quy tắc tự động nào?

**Đáp:** Không có rule chung; thiết kế phải xác định mandatory policy và conflict resolution.

**Câu 18.** Khi method trả CompletableFuture, after-returning đã chứng minh task success chưa?

**Đáp:** Chưa; cần quan sát async completion nếu đó là outcome cần đo.

## 28. Sơ đồ tổng kết

~~~text
                   SOURCE DECLARATIONS
                            |
           +----------------+-----------------+
           |                |                 |
           v                v                 v
       @TypeRule        @MethodRule        @ParamRule
       class/type       operation         parameter
           |                |                 |
           +----------------+-----------------+
                            |
                            v
               Compiler / class file
                            |
                    Retention / Target
                            |
                            v
              Annotation metadata resolver
              - inheritance/interface rules
              - override/bridge/proxy mapping
              - repeatable annotations
              - parameter index classification
                            |
                            v
                    Effective policy
                   validated and safe
                            |
                            v
              Pointcut + AOP advice engine
                 before / around / after
                            |
                            v
                Result / exception / audit
                 cleanup / async completion
~~~

**Kết luận:** Đánh dấu metadata ở **cấp kiểu, phương thức và tham số** giúp AOP mô tả những mối quan tâm xuyên cắt chính xác và có chủ đích. Nhưng ba vị trí này có semantics khác nhau: type-level thường đại diện scope/default, method-level chọn operation, parameter-level phân loại từng argument declaration. Cần hiểu @Target, @Retention, @Inherited, @Repeatable, reflection và khác biệt giữa interface/override/proxy trước khi xây pointcut. Annotation không tự enforce policy; **weaver/interceptor thực sự**, metadata resolution đúng, test coverage, redaction và exception semantics mới quyết định tính đúng đắn của hệ thống.

## Tài liệu tham khảo

- [Java Language Specification SE 17 — Annotations](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.6): khai báo annotation, meta-annotations và các quy tắc.
- [Java SE 17 — ElementType](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/annotation/ElementType.html): TYPE/METHOD/PARAMETER/TYPE_USE.
- [Java SE 17 — Retention](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/annotation/Retention.html): retention semantics.
- [Java SE 17 — Inherited](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/annotation/Inherited.html): class annotation inheritance.
- [Java SE 17 — Repeatable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/annotation/Repeatable.html): repeatable annotations.
- [Java SE 17 — AnnotatedElement](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AnnotatedElement.html): getAnnotationsByType và declared variants.
- [Java SE 17 — Parameter](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Parameter.html): parameter metadata.
- [AspectJ Programming Guide](https://eclipse.dev/aspectj/doc/latest/progguide/progguide.html): pointcut/advice, type/method annotation matching trong một ngôn ngữ AOP.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="annotation-metadata-004">Phạm vi lưu giữ và khả năng đọc siêu dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# Phạm vi lưu giữ và khả năng đọc siêu dữ liệu trong AOP

> **Mục tiêu:** Hiểu vòng đời của metadata, nhất là Java annotations, từ mã nguồn qua quá trình biên dịch, class file, class loading đến runtime; phân biệt **phạm vi lưu giữ (retention)** với **vị trí áp dụng (target)** và **khả năng truy cập (visibility/reading mechanism)**; biết vì sao một annotation có thể tồn tại trong class file nhưng không được Java Reflection trả về; hiểu tác động tới annotation-based pointcut, advice, weaving, proxy, inheritance, repeatable annotations, type-use, generic signatures, parameter names và class loader. Bài trình bày kiến thức Aspect-Oriented Programming (AOP) cơ bản và nâng cao nói chung, không giả định một bộ dệt hay framework chuyên biệt. Có hai chương trình Java 17 độc lập để thực hành.

## 1. Bài toán thực tế: có annotation nhưng advice không đọc được

Giả sử lập trình viên đánh dấu một method:

~~~java
@MarkedForAudit
public void transfer() {
    // logic nghiệp vụ
}
~~~

Một bộ chọn điểm can thiệp dựa trên annotation có thể cần biết method này có MarkedForAudit hay không. Tuy nhiên, chỉ nhìn thấy annotation ở source code **chưa đủ** để kết luận nó sẽ được tìm thấy qua Reflection lúc chạy.

Hãy đặt ba câu hỏi riêng:

1. **Annotation được phép đặt ở đâu?** Trên type, method, field, parameter, constructor, type-use hay một vị trí khác?
2. **Annotation được giữ đến giai đoạn nào?** Chỉ source, class file hoặc runtime?
3. **Ai đọc annotation, bằng cơ chế gì và tại thời điểm nào?** Compiler/annotation processor, class-file parser/weaver, Java Reflection, hay một index được tạo sẵn?

Cùng một annotation có thể phù hợp với một cơ chế biên dịch nhưng không phù hợp với một advice chỉ sử dụng Reflection runtime. Đây là nguồn gốc của nhiều vấn đề “pointcut không khớp” hoặc “metadata biến mất”.

## 2. Metadata là gì trong bối cảnh AOP?

**Metadata** là dữ liệu mô tả chương trình hoặc hoạt động thực thi, thay vì bản thân kết quả nghiệp vụ. Các nhóm thường gặp:

- **Type metadata:** class/interface, generic signature, modifiers, superclass và interfaces.
- **Member metadata:** methods, constructors, fields, tên, tham số, kiểu trả về, throws declaration.
- **Annotation metadata:** annotation type, element values, defaults, retention, targets, repeatable/inherited rules.
- **Source-related metadata:** debug line numbers, source filename và một số thông tin do compiler giữ lại.
- **Runtime join point context:** target object, current arguments, return value, exception, call-site identity hoặc control-flow context nếu cơ chế hỗ trợ.

Không nên đồng nhất **metadata tĩnh về mã chương trình** với **ngữ cảnh động của một lần thực thi**. Annotation @Audit trên method là mô tả có thể dùng lại giữa nhiều lời gọi; requestId của một lần gọi là dữ liệu runtime thuộc một execution/request scope.

Một aspect có thể kết hợp cả hai: dùng metadata tĩnh để quyết định có can thiệp hay không, rồi dùng context động khi advice thực sự chạy.

## 3. Vòng đời tổng quát của metadata

~~~text
Java source
   |
   | parser / compiler / annotation processing
   v
Compiled class files
   |
   | binary analysis / weaving / transformation
   v
Class loading + linking
   |
   | Java Reflection or runtime metadata APIs
   v
Runtime interception / advice
~~~

Mỗi tầng có tập dữ liệu riêng. **Không phải mọi thông tin từ source đều được giữ trong bytecode**, và không phải mọi dữ liệu có trong bytecode đều được Reflection cung cấp.

Một bộ dệt ở compile time có thể có thông tin source hoặc compiler model. Một class-file weaver có thể đọc attributes từ binary. Một interceptor chạy sau khi class đã được nạp thường dựa trên những thông tin mà runtime hoặc index riêng cung cấp.

Do đó khi một annotation-based aspect không chọn đúng method, câu đầu tiên nên là **“cơ chế nào đang đọc metadata ở giai đoạn nào?”**, thay vì cho rằng annotation chưa được viết đúng.

## 4. Ba mức RetentionPolicy trong Java

Java có ba chính sách lưu giữ annotation do java.lang.annotation.RetentionPolicy định nghĩa:

| RetentionPolicy | Ý nghĩa chính | Reflection runtime thông thường đọc được? |
|---|---|---|
| **SOURCE** | Annotation được compiler loại bỏ, không tồn tại như runtime annotation của class file | Không |
| **CLASS** | Annotation được ghi vào class file, nhưng không bắt buộc JVM giữ để Java Reflection truy cập | Không qua Reflection thông thường |
| **RUNTIME** | Annotation được giữ trong class file và JVM cung cấp để truy vấn qua Reflection phù hợp | Có, nếu truy vấn đúng phần tử và semantics |

Lưu ý kỹ thuật: retention không phải “cấp độ ưu tiên” của pointcut. Nó quyết định **annotation tồn tại tới đâu**; cơ chế AOP vẫn phải chọn đúng nơi tìm và đúng loại join point.

### 4.1. RetentionPolicy.SOURCE

Annotation SOURCE hữu ích cho compile-time checking, code generation, thông báo từ annotation processor hoặc một số công cụ phân tích source.

Sau khi compiler loại bỏ annotation này khỏi class file thông thường, một runtime advice sử dụng Method.getAnnotation sẽ không tìm thấy nó.

Không có nghĩa SOURCE annotation là “vô dụng với AOP”: một compiler-time transformation/analysis có quyền truy cập source hoặc compiler representation phù hợp vẫn có thể xử lý nó. Nhưng sau đó muốn can thiệp runtime, phải dựa vào **kết quả đã được sinh/biến đổi**, chứ không kỳ vọng Reflection thấy SOURCE annotation.

### 4.2. RetentionPolicy.CLASS

Annotation CLASS được ghi trong class file, thường nằm trong các annotation attributes không hiển thị với Reflection runtime.

Một class-file reader hoặc weaver có khả năng đọc binary metadata có thể dùng annotation CLASS để quyết định transformation trước khi thực thi. Nhưng Java Reflection như getAnnotation hoặc isAnnotationPresent không trả annotation này theo cách thông thường.

Đây là khác biệt quan trọng: **có trong .class không đồng nghĩa có trong kết quả Reflection**.

### 4.3. RetentionPolicy.RUNTIME

Annotation RUNTIME được ghi trong class file và có thể được cung cấp qua các API Reflection tương ứng, ví dụ:

- Class.getAnnotation.
- Method.getDeclaredAnnotation.
- Field.getAnnotations.
- Constructor.getAnnotation.
- Parameter.getAnnotation.
- AnnotatedType.getAnnotation khi metadata được gắn trên type-use tương ứng.

Tuy nhiên, không phải cứ RUNTIME là mọi phương thức trên mọi object đều trả ra annotation. Còn phải chọn **đúng AnnotatedElement, đúng reflection object, đúng luật inheritance/repeatability, đúng class loader**.

## 5. Không khai báo @Retention mặc định ra sao?

Nếu một annotation type **không khai báo @Retention**, Java áp dụng retention mặc định **CLASS**.

Ví dụ minh họa:

~~~java
@interface InternalMarker {}
~~~

Nó không tương đương với RUNTIME. Nếu một thư viện AOP chỉ có runtime reflection, việc quên @Retention(RetentionPolicy.RUNTIME) là một nguyên nhân rất thường gặp khiến pointcut annotation không nhận diện được dấu đánh dấu.

Nên quy định rõ retention ngay khi thiết kế annotation dành cho runtime advice, thay vì phụ thuộc mặc định không phù hợp.

## 6. @Retention tự nó cũng là metadata

Một annotation type được khai báo như:

~~~java
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@interface Audited {}
~~~

Bản thân @Retention là một **meta-annotation**, tức annotation gắn trên khai báo của annotation type khác.

Có thể truy vấn mô tả của annotation type nếu meta-annotation ấy hiện diện phù hợp. Nhưng điều đó không thay thế kiểm tra annotation trên **method cụ thể**.

Cần phân biệt:

- Audited.class: annotation type.
- Audited.class.getAnnotation(Retention.class): retention metadata của annotation type.
- Method.getAnnotation(Audited.class): annotation instance khai báo trên method, nếu có và nếu runtime đọc được.

Hai thao tác khác nhau trả lời hai câu hỏi khác nhau.

## 7. @Target không phải @Retention

**@Target** xác định annotation type được dùng ở những vị trí nào theo các ElementType cho phép. Ví dụ:

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@interface BoundaryMarker {}
~~~

**@Retention** xác định mức lưu giữ; **@Target** xác định vị trí hợp lệ. Một annotation có @Target(METHOD) và @Retention(SOURCE) vẫn không được runtime Reflection thấy.

Ngược lại, @Retention(RUNTIME) và @Target(PARAMETER) không có nghĩa Class.getAnnotation sẽ thấy nó: annotation được gắn trên **parameter declaration**, phải truy vấn Parameter tương ứng.

### 7.1. Một số ElementType hay gặp

| ElementType | Vị trí | Cách đọc runtime điển hình khi retention RUNTIME |
|---|---|---|
| TYPE | Class, interface, enum hoặc annotation type declaration phù hợp | Class |
| METHOD | Method declaration | Method |
| CONSTRUCTOR | Constructor declaration | Constructor |
| FIELD | Field declaration | Field |
| PARAMETER | Formal parameter declaration | Parameter |
| ANNOTATION_TYPE | Khai báo annotation type | Class của annotation type |
| TYPE_PARAMETER | Khai báo type parameter | TypeVariable với annotation APIs |
| TYPE_USE | Vị trí sử dụng kiểu, ví dụ return type, generic argument | AnnotatedType và các biến thể |

Không nên lấy cùng một annotation chỉ vì nó được đặt “gần” method trong source. **Vị trí cú pháp và API Reflection tương ứng rất quan trọng.**

## 8. TYPE_USE có nghĩa khác METHOD

Ví dụ:

~~~java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface NonNullType {}

class Example {
    public @NonNullType String display() {
        return "ok";
    }
}
~~~

Đây chỉ là đoạn minh họa, không phải một file Java độc lập.

NonNullType được gắn trên **return type use**, không nhất thiết là annotation của method declaration. Đọc qua Method.getAnnotations có thể không thấy; hãy xét Method.getAnnotatedReturnType rồi đọc annotation trên AnnotatedType.

Nếu một annotation được khai báo cho cả METHOD và TYPE_USE, cần xem cách đặt trong source và các API đọc phù hợp. Không thể suy method-level pointcut match từ mọi type-use annotation nằm gần chữ ký.

## 9. Ví dụ Java 17 số 1 — SOURCE, CLASS, RUNTIME và @Inherited

Chương trình này dùng ba annotation với retention khác nhau trên cùng class và method. Nó còn dùng một @Inherited annotation trên class cha để chứng minh:

1. Reflection chỉ đọc được RuntimeTag trong ba loại retention trên một class/method.
2. @Inherited có tác dụng ở **class-level annotation inheritance** theo quy tắc Java.
3. Override method trong subclass **không tự thừa hưởng annotation declaration của method cha**.

Đây là Java Reflection thuần để quan sát metadata. Nó không mô phỏng một engine AOP hoàn chỉnh.

Lưu file **RetentionVisibilityDemo.java**:

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

public class RetentionVisibilityDemo {
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @interface SourceTag {}

    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @interface ClassTag {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @interface RuntimeTag {
        String value();
    }

    @Inherited
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface InheritedPolicy {
        String value();
    }

    @SourceTag
    @ClassTag
    @RuntimeTag("sample")
    static class Sample {
        @SourceTag
        @ClassTag
        @RuntimeTag("work")
        public void work() {}
    }

    @InheritedPolicy("from-parent")
    static class Parent {
        @RuntimeTag("parent-method")
        public void action() {}
    }

    static class Child extends Parent {
        @Override
        public void action() {}
    }

    public static void main(String[] args) throws Exception {
        Class<Sample> type = Sample.class;
        Method work = type.getDeclaredMethod("work");

        System.out.println("[type] source="
                + type.isAnnotationPresent(SourceTag.class));
        System.out.println("[type] class="
                + type.isAnnotationPresent(ClassTag.class));
        System.out.println("[type] runtime="
                + type.getAnnotation(RuntimeTag.class).value());

        System.out.println("[method] source="
                + work.isAnnotationPresent(SourceTag.class));
        System.out.println("[method] class="
                + work.isAnnotationPresent(ClassTag.class));
        System.out.println("[method] runtime="
                + work.getAnnotation(RuntimeTag.class).value());

        System.out.println("[child] inherited="
                + Child.class.getAnnotation(
                        InheritedPolicy.class).value());
        System.out.println("[child] declared="
                + (Child.class.getDeclaredAnnotation(
                        InheritedPolicy.class) != null));

        Method overridden =
                Child.class.getDeclaredMethod("action");
        System.out.println("[override] runtime="
                + overridden.isAnnotationPresent(RuntimeTag.class));

        Method parentMethod =
                Parent.class.getDeclaredMethod("action");
        System.out.println("[parent-method] runtime="
                + parentMethod.getAnnotation(
                        RuntimeTag.class).value());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac RetentionVisibilityDemo.java
java RetentionVisibilityDemo
~~~

**Stdout xác định:**

~~~text
[type] source=false
[type] class=false
[type] runtime=sample
[method] source=false
[method] class=false
[method] runtime=work
[child] inherited=from-parent
[child] declared=false
[override] runtime=false
[parent-method] runtime=parent-method
~~~

### 9.1. Vì sao SOURCE và CLASS đều trả false?

isAnnotationPresent dùng Reflection runtime đối với loại annotation cần truy vấn. SOURCE không được giữ trong class file; CLASS có thể còn trong class file nhưng không phải runtime-visible annotation qua API này.

Cả hai trả false ở đây **không có nghĩa** compiler hoặc binary transformer không thể đọc ClassTag khi dùng đúng công cụ và giai đoạn tương ứng.

### 9.2. Vì sao RuntimeTag đọc được?

RuntimeTag có retention RUNTIME, được gắn đúng class/method và truy vấn trên đúng AnnotatedElement. getAnnotation trả annotation instance, nên có thể lấy element value.

Kết quả phản ánh **vị trí khai báo thực tế**, không phải một pointcut match tự động.

### 9.3. @Inherited không phải copy annotation xuống subclass

Child không khai báo trực tiếp InheritedPolicy, nhưng Class.getAnnotation có thể thấy annotation theo semantics @Inherited đối với annotation type ở class hierarchy.

getDeclaredAnnotation chỉ xét **khai báo trực tiếp** trên Child, nên trả null và biến declared là false.

Không được suy rằng class file của Child thực sự có một bản annotation được copy vào phần khai báo. Đây là quy tắc truy vấn được runtime hỗ trợ.

### 9.4. Override method không tự kế thừa method annotation

Child.action override Parent.action nhưng không khai báo RuntimeTag. Reflection trên **Child declared Method** không thấy RuntimeTag.

Parent.action vẫn có annotation của chính nó. Nếu một pointcut hoặc adapter muốn coi annotation trên method gốc như áp dụng cho override, nó phải **định nghĩa và thực hiện resolution strategy** tương ứng. @Inherited không tự cung cấp ngữ nghĩa đó cho method.

### 9.5. Vì sao ví dụ không chứng minh runtime weaving?

Reflection cho thấy metadata đọc được bằng JDK. Một engine AOP còn cần **khả năng chặn join point thích hợp** và quyết định quy tắc matching riêng. RUNTIME retention là điều kiện thuận lợi cho reflection-based matching nhưng không tự làm một method trở thành join point có thể advice trong mọi engine.

## 10. CLASS retention trong class file có thể trông thế nào?

Một Java class file có các attributes dùng để biểu diễn annotation. Trong thực hành, công cụ phân tích binary như javap -v có thể giúp quan sát những nhóm metadata:

- **RuntimeVisibleAnnotations:** dùng để chứa những annotation declaration có runtime visibility.
- **RuntimeInvisibleAnnotations:** dùng cho một số annotation declaration giữ trong class file nhưng không runtime-visible.
- Các biến thể attributes tương ứng cho parameter annotations và type annotations.

Tên attributes ở mức JVM class-file format; không đồng nghĩa Reflection có một API chung để đọc mọi RuntimeInvisibleAnnotations.

### 10.1. Cách tự khảo sát binary

Sau khi biên dịch ví dụ 1, có thể dùng công cụ từ JDK như:

~~~bash
javap -v RetentionVisibilityDemo
~~~

Để khảo sát nested class cần truyền tên binary class hoặc tệp .class thích hợp, ví dụ trên một shell hỗ trợ quote:

~~~bash
javap -v 'RetentionVisibilityDemo$Sample.class'
~~~

Đây là thao tác khảo sát, không cung cấp một stdout cố định vì output của javap -v dài và có thể khác theo compiler/JDK build.

Điều cần quan sát là RuntimeTag có metadata runtime-visible, ClassTag có thể xuất hiện như runtime-invisible trong class file, còn SourceTag không có annotation declaration entry tương ứng trong class file.

## 11. Annotation processor đọc metadata ở giai đoạn nào?

Java annotation processing hoạt động trong bối cảnh compilation, dùng mô hình elements/types do compiler cung cấp qua các API xử lý annotation tương ứng.

Một processor có thể nhận thông tin về annotations ở source/compilation model, kể cả annotation có retention SOURCE, nếu annotation đó hiện diện ở giai đoạn xử lý phù hợp.

Nhưng processor không tự làm runtime Reflection thấy SOURCE annotation. Nếu cần hành vi runtime, một processor có thể **sinh mã, metadata index hoặc các cấu trúc hỗ trợ** để runtime sử dụng, trong phạm vi build pipeline được thiết kế.

### 11.1. Không nhầm annotation processor với runtime advice

Annotation processor không có nghĩa mỗi lần một method được gọi thì processor lại chạy. Nó thường chạy trong quá trình compile, còn advice là hành vi tại những runtime join points (hoặc code đã được transform).

Đây là hai tầng khác nhau:

~~~text
Compile-time:
  annotation discovery -> validation / code generation

Runtime:
  selected join point -> advice executes
~~~

Nếu build không chạy processor, output sinh mã/index có thể thiếu, dù annotation type vẫn có retention đúng. Cần kiểm tra cả **build configuration**.

## 12. Class-file weaving có thể dùng dữ liệu mà Reflection không thấy

Một binary weaver đọc bytecode trước runtime có thể dựa trên annotation với retention CLASS, vì annotation này được compiler ghi vào class file.

Tuy nhiên, có các điều kiện:

1. Class thực sự nằm trong phạm vi được weaver quét.
2. Công cụ hỗ trợ đọc annotation attribute tương ứng.
3. Binary còn annotation đó sau compiler/optimizer/transformer trước đó.
4. Annotation được gắn đúng element.
5. Matching rule của weaver thực sự cho phép selection theo metadata ấy.
6. Weaving được áp dụng trước khi class cần sử dụng theo mô hình tương ứng.

Do đó không nên khẳng định tất cả các engine chỉ làm runtime reflection. Cũng không nên khẳng định **mọi engine đọc được CLASS annotations**. Cơ chế là yếu tố quyết định.

## 13. RUNTIME retention không bảo đảm mọi annotation đều được nhìn thấy

Các nguyên nhân phổ biến khiến getAnnotation trả null dù annotation type có RUNTIME:

- Truy vấn nhầm Class thay vì Method.
- Truy vấn nhầm Method declaration thay vì return AnnotatedType.
- Truy vấn interface method trong khi annotation nằm trên implementation method, hoặc ngược lại.
- Phương thức con override nhưng không mang annotation khai báo của phương thức cha.
- Dùng getDeclaredAnnotation trong khi muốn xem @Inherited class-level annotation.
- Repeatable annotations được compiler đóng vào container.
- Annotation chỉ gắn trên bridge/synthetic method khác.
- Class object đến từ class loader không phù hợp.
- Tên annotation giống nhau nhưng class identity khác nhau.
- Công cụ AOP không hỗ trợ join point dù reflection đọc được metadata.

**Cần chẩn đoán đồng thời retention, vị trí, API đọc và cơ chế can thiệp.**

## 14. @Inherited có phạm vi hẹp

@Inherited là meta-annotation có tác động tới việc tra cứu một annotation type trên **class hierarchy** theo quy tắc của Class annotation API.

Nó không đồng nghĩa:

- Annotation method override tự được kế thừa.
- Annotation field của superclass tự trở thành declared annotation trên field subclass.
- Annotation từ interface được Class.getAnnotation kế thừa theo mọi trường hợp.
- Annotation type-use được tự chuyển từ một vị trí type-use cha sang con.
- Annotation được copy vào bytecode của subclass.

Nếu một annotation-based pointcut muốn nhận diện interface metadata khi target implementation không khai báo annotation trực tiếp, cần một **quy tắc lookup riêng** (hoặc tính năng công cụ được đặc tả), không dựa mù quáng vào @Inherited.

## 15. Annotation trên interface so với implementation

Xét mô hình:

~~~text
interface Repository:
   @Audited
   find()

class SqlRepository implements Repository:
   find()   // không ghi annotation ở đây
~~~

Reflection trên Method object của Repository.find có thể thấy annotation; Reflection trên method declaration SqlRepository.find không tự thấy cùng annotation.

Một interceptor có thể nhận Method mô tả interface hoặc implementation tùy cơ chế proxy/call path. Nếu adapter đọc annotation trực tiếp từ Method mà runtime cung cấp mà không cân nhắc nguồn Method, behavior có thể thay đổi khi đổi cơ chế can thiệp.

### 15.1. Thiết kế một lookup strategy rõ ràng

Các lựa chọn khái niệm:

1. **Exact declaration only:** chỉ annotation trên Method đang xét.
2. **Implementation-first:** ưu tiên concrete implementation rồi xét interface.
3. **Interface-first:** lấy hợp đồng interface trước, implementation có quy tắc override riêng.
4. **Hierarchy walk:** duyệt superclasses/interfaces theo thuật toán được định nghĩa.
5. **Explicit conflict:** báo lỗi khi hai nguồn đưa giá trị mâu thuẫn.

Không có một chiến lược duy nhất đúng trong mọi domain. Nhưng phải **tài liệu hóa precedence**, tránh “annotation thay đổi theo proxy type” ngoài ý muốn.

## 16. getAnnotation và getDeclaredAnnotation khác nhau

Trên Class:

- getDeclaredAnnotation chọn annotation **khai báo trực tiếp** trên class đó.
- getAnnotation có thể xét ngữ nghĩa kế thừa cho annotation type đánh dấu @Inherited.

Trên Method, Field hoặc Parameter, không được suy có cùng cơ chế kế thừa kiểu class. Annotation declared của method con và method cha là những khai báo khác nhau.

Ngoài ra có API **getAnnotationsByType** và **getDeclaredAnnotationsByType**, quan trọng với annotation lặp. Nếu dùng API trả một annotation đơn trong khi annotation có thể lặp, kết quả có thể không phản ánh đủ dữ liệu.

## 17. Repeatable annotations có container

Java hỗ trợ khai báo annotation có thể lặp bằng @Repeatable. Khi có nhiều annotation cùng loại trên một declaration, compiler có thể biểu diễn chúng thông qua một **container annotation**.

Ví dụ khái niệm:

~~~text
@Rule("audit")
@Rule("trace")
void execute()
~~~

Một lời gọi getAnnotation(Rule.class) không phải API phù hợp để truy vấn **toàn bộ** các Rule khi chúng được đóng vào container.

Nên dùng getAnnotationsByType(Rule.class) hoặc getDeclaredAnnotationsByType theo nhu cầu inheritance/declaration. Khi đọc binary, cần hiểu cách container được biên dịch và retention/target constraints tương ứng.

## 18. Ví dụ Java 17 số 2 — repeatable, parameter và type-use

Chương trình này chứng minh:

1. Hai @Rule được đọc bằng getAnnotationsByType.
2. Container @Rules có thể được thấy ở Method.
3. Parameter annotation có API riêng.
4. Type-use annotation trên return type được đọc bằng getAnnotatedReturnType.
5. Parameter name metadata chỉ ổn định khi biên dịch với cờ -parameters trong bài này.

Lưu file **MetadataTraversalDemo.java**:

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.stream.Collectors;

public class MetadataTraversalDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Repeatable(Rules.class)
    @interface Rule {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Rules {
        Rule[] value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @interface Argument {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE_USE)
    @interface NonEmpty {}

    static class Api {
        @Rule("audit")
        @Rule("trace")
        public @NonEmpty String convert(
                @Argument("input") String input) {
            return input;
        }
    }

    public static void main(String[] args) throws Exception {
        Method method = Api.class.getDeclaredMethod(
                "convert", String.class);

        Rule[] rules = method.getAnnotationsByType(Rule.class);
        String joined = Arrays.stream(rules)
                .map(Rule::value)
                .collect(Collectors.joining(","));

        System.out.println("[method] rule-count="
                + rules.length);
        System.out.println("[method] rules=" + joined);
        System.out.println("[method] has-container="
                + (method.getAnnotation(Rules.class) != null));
        System.out.println("[method] direct-rule="
                + (method.getAnnotation(Rule.class) != null));

        Parameter parameter = method.getParameters()[0];
        System.out.println("[param] annotation="
                + parameter.getAnnotation(Argument.class).value());
        System.out.println("[param] name=" + parameter.getName());
        System.out.println("[param] name-present="
                + parameter.isNamePresent());

        boolean returnMarked =
                method.getAnnotatedReturnType()
                        .isAnnotationPresent(NonEmpty.class);
        System.out.println("[return-type] non-empty="
                + returnMarked);
        System.out.println("[method] has-type-use-as-declaration="
                + method.isAnnotationPresent(NonEmpty.class));
    }
}
~~~

Biên dịch và chạy **đúng cờ -parameters**:

~~~bash
javac -parameters MetadataTraversalDemo.java
java MetadataTraversalDemo
~~~

**Stdout xác định khi dùng JDK 17 và lệnh trên:**

~~~text
[method] rule-count=2
[method] rules=audit,trace
[method] has-container=true
[method] direct-rule=false
[param] annotation=input
[param] name=input
[param] name-present=true
[return-type] non-empty=true
[method] has-type-use-as-declaration=false
~~~

### 18.1. Tại sao getAnnotation(Rule.class) là false?

Hai Rule khai báo trên method được biểu diễn dưới một container Rules theo luật repeatable annotation. getAnnotation(Rule.class) không phải phép “mở mọi container và trả một trong hai Rule”.

getAnnotationsByType xử lý những annotations trực tiếp/gián tiếp hiện diện theo semantics Java. Kết quả gồm hai Rule, theo thứ tự được bảo toàn phù hợp.

### 18.2. Tại sao container tồn tại?

@Repeatable(Rules.class) trỏ tới container annotation type. Trong trường hợp có hai Rule, Method có annotation container Rules chứa hai phần tử.

Nếu một AOP adapter chỉ đọc getDeclaredAnnotations và không unwrap repeatable container, có thể không nhận diện từng Rule riêng. Chọn API đọc phù hợp là trách nhiệm metadata lookup.

### 18.3. Vì sao annotation parameter không nằm trên Method trực tiếp?

@Argument được gắn với formal parameter. Để lấy nó, ví dụ dùng getParameters()[0] rồi getAnnotation(Argument.class).

Không nên dùng annotation method lookup để suy một parameter đã được đánh dấu. Một annotation-based pointcut dành cho parameter cần biết mapping parameter-index và những loại parameter mà mô hình hỗ trợ.

### 18.4. Vì sao @NonEmpty không xuất hiện như annotation declaration của method?

NonEmpty có @Target(TYPE_USE). Trong ví dụ nó gắn trên **String return type use**, nên method.getAnnotatedReturnType thấy nó, còn method.isAnnotationPresent không thấy annotation declaration thuộc METHOD.

Đây là khác biệt giữa **metadata về method** và **metadata về type nằm trong chữ ký của method**.

### 18.5. Vì sao phải dùng -parameters?

Java class file có thể giữ method parameter name metadata nhờ tùy chọn biên dịch -parameters. Nếu không có, Reflection có thể cung cấp tên tổng hợp như arg0 và isNamePresent() là false.

Annotation parameter không tự đòi hỏi -parameters để được đọc; đây là hai nhóm metadata khác nhau. Cờ -g để thêm debug information cũng không thay thế trực tiếp hợp đồng -parameters của Parameter.getName().

## 19. Sự khác nhau giữa annotation element và annotation instance

Một annotation type có thể khai báo elements:

~~~java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface LogRule {
    String category() default "general";
    int level() default 1;
}
~~~

Đây là một đoạn Java minh họa.

**Element values** có thể đến từ giá trị người dùng chỉ định hoặc default trong annotation type. Khi Reflection trả annotation instance, gọi các element methods như category() đọc giá trị hiệu lực theo semantics runtime.

Điều này quan trọng khi xây cache:

- Cache chỉ tên annotation type có thể thiếu element values.
- Cần key theo nơi khai báo cụ thể nếu các method có giá trị khác nhau.
- Một default value thay đổi theo phiên bản annotation type có thể làm kết quả mới khác giá trị cũ.
- Nếu class file và annotation type bị lệch phiên bản, runtime annotation parsing có thể gặp lỗi.

Không tự gán rằng hai method có cùng annotation type sẽ có cùng chính sách xử lý.

## 20. Metadata có thể tồn tại nhưng không còn truy cập được đúng cách

Một annotation retention RUNTIME trong .class có thể vẫn không được công cụ tìm thấy vì:

1. Công cụ chọn nhầm method.
2. Class được nạp từ bản khác.
3. Runtime đang dùng một class loader khác.
4. Một weaver/proxy chỉ quan sát interface method.
5. Annotation được đặt trên generic bridge counterpart.
6. Tên annotation được kiểm tra như String thay vì class identity.
7. Bộ tối ưu/biến đổi binary không giữ metadata như mong đợi.
8. API tìm annotation không hỗ trợ trường hợp repeatable/type-use cần xét.
9. Công cụ chọn sai loại join point.
10. Annotation query bị thực hiện sau khi metadata đã được snapshot theo rules riêng của engine.

Nên tách một phép thử **JDK Reflection thuần** khỏi một phép thử **AOP pointcut selection thực tế** để xác định lỗi ở tầng nào.

## 21. Reflection method object chưa chắc là implementation method

Trong một số cơ chế proxy qua interface, một interceptor có thể nhận Method từ interface. Với class-level weaving hoặc reflection trực tiếp trên target, nó có thể nhận Method trên concrete class.

Nếu @Audit được đặt trên implementation nhưng Method đang được hỏi thuộc interface chưa đánh dấu, isAnnotationPresent có thể là false.

Một lookup strategy có thể phải phân giải method concrete bằng:

- Tên.
- Loại tham số thực hoặc descriptor.
- Declaring type và runtime target type.
- Visibility/override rules.
- Bridge/synthetic method handling.
- Interface hierarchy policy.

Không chỉ tìm theo tên method, bởi **overloads** có thể cùng tên nhưng annotation khác nhau.

## 22. Annotation trên superclass method và override

Trường hợp:

~~~text
Base.process() has @Logged
Child.process() overrides with no annotation
~~~

Java không tự sao chép @Logged xuống Child.process. Một AOP policy có thể muốn kế thừa ngữ nghĩa annotation từ Base, nhưng đó là **quy tắc lựa chọn do hệ thống định nghĩa**.

Cần làm rõ:

- Annotation ở Base có hiệu lực trên override không?
- Annotation ở Child có ghi đè hay cộng thêm?
- Nếu Base/Interface có annotation mâu thuẫn, ai thắng?
- Nếu Child không có override mà kế thừa method, Method reflection nào được truy vấn?
- Nếu class có bridge methods, cần normalize về method logic nào?

Một quy tắc không rõ ràng có thể khiến việc thêm override làm mất audit mà không ai nhận ra.

## 23. Bridge và synthetic methods trong Java generics

Java compiler có thể sinh **bridge methods** để bảo toàn polymorphism sau type erasure. Chúng là methods runtime thực tế ở bytecode và có thể xuất hiện trong Reflection.

Nếu một AOP tool quan sát method execution, một call có thể đi qua bridge rồi tới implementation method tùy bytecode và dispatch. Không nên tự đếm chúng là hai thao tác nghiệp vụ riêng nếu policy chỉ muốn một logical invocation.

Khi tìm annotation:

- Method.isBridge() giúp phân biệt bridge.
- Method.isSynthetic() cho biết method được tạo tổng hợp ở một số trường hợp.
- Generic signature và erased descriptor khác nhau.
- Annotation có thể nằm trên declaration logic hoặc được compiler xử lý theo rules riêng.

Không tự giả định mọi compiler copy tất cả annotations giống nhau sang bridge method, nhất là khi xử lý version khác nhau. Cần dùng lookup strategy và test trên compiled classes thật.

## 24. Annotation TYPE_PARAMETER và TYPE_USE trong generic APIs

Một annotation có thể gắn trên khai báo type variable hoặc chỗ sử dụng kiểu tổng quát, ví dụ:

~~~text
class Container<@BoundTag T> { ... }
List<@NonNullType String> values
~~~

Đây là mô tả khái niệm; annotation definitions và các type bounds phải hợp lệ tương ứng mới biên dịch được.

Reflection dùng các APIs khác nhau: TypeVariable có thể là AnnotatedElement cho TYPE_PARAMETER annotations; AnnotatedParameterizedType và AnnotatedType variants cung cấp thông tin về TYPE_USE annotations trên type arguments.

Một method-level pointcut chỉ đọc Method.getAnnotation có thể bỏ qua những annotations ở **type parameter hoặc nested type-use**. Nhưng không có nghĩa mọi AOP engine cần hỗ trợ chúng làm điều kiện chọn join point; đó là khả năng riêng.

## 25. Parameter annotations và parameter names là hai lớp metadata

Ví dụ một method có hai parameters:

~~~text
update(@Sensitive String token, int retries)
~~~

Annotation @Sensitive có thể được lưu dưới dạng parameter annotation nếu retention phù hợp. Parameter name token và retries có thể **không có** trong Reflection nếu compiler không ghi MethodParameters theo cách cần thiết.

Điều đó có nghĩa:

- Có thể biết **parameter 0** được đánh dấu Sensitive.
- Nhưng chưa chắc đọc được tên token.
- Một advice redact arguments nên ưu tiên **vị trí/annotation metadata được xác nhận**, không dựa toàn bộ vào tên tham số khi tên không có.
- Nếu một index đọc source dùng tên nhưng runtime không có, mapping phải được xây nhất quán.

Trong code bảo mật, không được bỏ redaction chỉ vì Parameter.getName() trả arg0.

## 26. Reflection không tự cung cấp mọi thông tin source

Compiler có thể ghi source filename và line numbers trong debug attributes; một số dữ liệu bị loại khi tối ưu hoặc biên dịch với flags khác.

Reflection tiêu chuẩn không cung cấp trực tiếp đầy đủ tất cả line numbers hoặc nguồn gốc pointcut như một AST. Những công cụ bytecode hoặc runtime diagnostics khác có thể dùng class-file attributes để hiển thị line/file, nhưng thông tin có thể thiếu hoặc không chính xác ở generated code.

Đặc biệt với lambdas, synthetic methods và transformed bytecode, không nên suy “line source = join point identity duy nhất” khi định nghĩa chính sách AOP.

## 27. Class loader và danh tính annotation type

Trong JVM, danh tính runtime của một type phụ thuộc vào **binary name và defining class loader**. Hai annotation classes cùng tên nhưng được nạp bởi hai class loaders khác nhau không nhất thiết là cùng type.

Khi dùng:

~~~java
element.getAnnotation(Audited.class)
~~~

Audited.class là một **Class object cụ thể**. Nếu annotation trong target metadata được resolve qua loader khác và type identity không tương thích, lookup có thể không như người viết mong đợi.

Các bối cảnh phức tạp như plugin containers, isolated modules hoặc dynamic loading cần test đúng class loader topology; không dựa vào tên annotation in ra để kết luận có thể cast/truy vấn.

## 28. Module boundaries và access constraints

Java modules và visibility có thể ảnh hưởng cách code Reflection truy cập khai báo và invoke members. Việc **đọc annotation metadata** và việc **truy cập/call một private method** là hai thao tác khác nhau với constraints riêng.

Một AOP runtime có thể có quyền bytecode instrumentation không giống một thư viện Reflection chạy từ module khác.

Không tự coi @Retention(RUNTIME) là quyền vượt qua mọi giới hạn module accessibility, security policy hoặc class loader. Phạm vi can thiệp phải được xác minh độc lập với metadata visibility.

## 29. Annotations không phải lệnh thực thi

Đặt @Timed trên method không tự đo thời gian. Đặt @Audit không tự sinh audit event. Annotation chỉ là metadata; phải có một thành phần hiểu và thực thi chính sách ấy:

~~~text
Annotation declaration
    |
    +-- compiler/processor recognizes it
    |
    +-- weaver uses it as selection/transform rule
    |
    +-- runtime interceptor queries it
    |
    +-- application code reads it explicitly
~~~

Nếu không có consumer thích hợp, annotation có thể chỉ là tài liệu hoặc dấu đánh dấu.

Một Java Reflection query thành công cũng không chứng minh method được tự động interception; phải có join point selection và advice invocation đúng runtime path.

## 30. Annotation-based pointcut phải định nghĩa “annotation ở đâu?”

Một rule kiểu “chặn các method @Audited” còn mơ hồ nếu không nói:

- Annotation đặt trên method **declaration** hay class?
- Nếu annotation trên interface method thì implementation có được tính không?
- Nếu trên method cha bị override thì có tính không?
- Nếu annotation lặp, có đọc toàn bộ rules không?
- Nếu annotation là meta-annotation trên một annotation khác thì có được xét không?
- Nếu method có bridge hoặc synthetic counterpart, chọn method nào?
- Có xét runtime target type hay chỉ declared type?
- Nếu annotation ở return type-use thì có được match không?

Một annotation-driven pointcut tốt phải có **lookup semantics rõ**, không dựa vào tên @Audited chung chung rồi hi vọng mọi framework diễn giải giống nhau.

## 31. Meta-annotation không tự là annotation trực tiếp

Ví dụ:

~~~text
@AuditStereotype
@interface CriticalAction {}

@CriticalAction
void transfer()
~~~

Nếu AuditStereotype được gắn lên **annotation type CriticalAction**, thì transfer trực tiếp có @CriticalAction, không tự có @AuditStereotype trên method.

Một tool có thể hỗ trợ **meta-annotation search**, bằng cách đọc annotation type của các annotations gắn trực tiếp lên method và tiếp tục kiểm tra metadata của chúng. Nhưng đó là **chức năng traversal riêng**, không phải kết quả mặc định của Method.getAnnotation(AuditStereotype.class).

Cần có giới hạn chiều sâu, xử lý cycles/duplicates và precedence nếu tự xây traversal logic. Một annotation type còn có meta-annotations tiêu chuẩn; không nên mọi metadata cấp hệ thống đều bị coi thành nghiệp vụ.

## 32. Cache metadata: đúng đắn trước hiệu năng

Metadata tĩnh thường được dùng nhiều lần. Một AOP runtime có thể cache kết quả resolution để tránh Reflection lặp lại trên hot path.

Những key có thể cần:

- Method object hoặc method descriptor đầy đủ.
- Declaring type và target runtime type khi semantics phụ thuộc.
- Class loader identity.
- Loại annotation hoặc loại query.
- Chiến lược inheritance/interface resolution.
- Có cần đọc repeatable/type-use hay không.

Nếu cache chỉ theo tên method, hai overloads khác annotations có thể bị trộn. Nếu cache giữ strong references tới Class/Method của plugin đã được unload, cache có thể giữ class loader sống lâu ngoài ý định.

Một design cần cân nhắc weak keys, lifetime scope và cache invalidation theo mô hình class loading thực tế.

### 32.1. Cẩn thận với cache giữa các class loaders

Hai classes có cùng fully-qualified name nhưng loader khác nhau có thể mang annotations khác nhau. Cache key chỉ dùng tên String làm kết quả một môi trường dùng sang môi trường khác là không an toàn.

Trong AOP có load-time transformations hoặc plugin reload, cần xem cấu trúc metadata có thay đổi theo phiên bản class/runtime không.

## 33. Precompiled indexes và generated metadata

Một cách khác Reflection runtime là tạo **metadata index** ở build time:

~~~text
Source / class files
    |
    v
Index generator
    |
    v
Metadata registry
    |
    v
Runtime interceptor lookup
~~~

Ưu điểm tiềm năng:

- Giảm Reflection on hot path.
- Có thể dùng dữ liệu mà build-time tool được phép đọc.
- Dễ kiểm tra static consistency.
- Có thể tạo mapping từ annotation sang rule IDs.

Nhưng phải quản lý:

- Phiên bản index so với class files thực sự chạy.
- Class loader/module mapping.
- Method descriptors cho overload/bridge.
- Dữ liệu đã transform sau build.
- Invalidations khi deploy/reload.
- Security của registry và dữ liệu nhạy cảm.

Không mặc định mọi hệ AOP dùng index hoặc index luôn chính xác. Với compilation khác nhau, index stale có thể tệ hơn Reflection chậm nhưng đúng.

## 34. Chọn retention theo consumer thực tế

| Nhu cầu | Lựa chọn nên xem xét |
|---|---|
| Chỉ compile-time validation | SOURCE có thể đủ |
| Tool binary đọc annotation trong class file trước runtime | CLASS có thể phù hợp |
| Java Reflection runtime cần tìm annotation trên method/type | RUNTIME thường cần |
| Một công cụ code generation xử lý annotation khi compile | SOURCE, CLASS hoặc RUNTIME tùy pipeline |
| Cần runtime match theo annotation đã biến đổi | Xem cả retention lẫn transformation và lookup strategy |
| Annotation chỉ làm marker trong tài liệu source | Không nhất thiết cần RUNTIME |
| Metadata type-use cần được runtime đọc | RUNTIME + TYPE_USE + AnnotatedType API đúng |

Chọn retention **không nên chỉ vì “càng giữ lâu càng tốt”**. RUNTIME metadata có overhead class file/runtime và làm API metadata công khai hơn. Nhưng nếu runtime interceptor thật sự phụ thuộc Reflection thì SOURCE/CLASS sẽ không đáp ứng.

## 35. Chi phí của metadata khi dùng rộng

Các chi phí có thể gồm:

- Kích thước class file khi lưu annotation/attributes.
- Nạp/parse metadata ở runtime.
- Reflection lookup lặp lại trong advice.
- Duyệt hierarchy và interfaces.
- Giải quyết repeatable container.
- Phân tích generic AnnotatedType tree.
- Tạo/copy các annotation values.
- Cache giữ Class/Method/class loader references.
- Thời gian match pointcut động nếu cứ lặp metadata resolution.

Cách tối ưu:

1. Chuẩn hóa method key một lần.
2. Cache những kết quả tĩnh theo class loader/lifetime phù hợp.
3. Tránh Reflection mỗi invocation khi không cần.
4. Chỉ duyệt hierarchy theo rule đã định.
5. Không serialize raw annotations vào mọi log.
6. Đo latency trước/sau trong điều kiện weaving thật.
7. Tránh để cache chứa unbounded entries khi có dynamic class loading.

Không đặt mục tiêu tối ưu trước khi bảo đảm **annotation lookup đúng semantics**.

## 36. An toàn và bảo vệ metadata

Annotation values có thể chứa:

- Endpoint names hoặc internal resource paths.
- Thông tin phân loại dữ liệu.
- ID của policy hoặc tên tenant theo thiết kế không phù hợp.
- Tham số điều khiển logging.
- Class references, enum constants hoặc arrays.

Không nên đặt passwords, raw tokens hoặc secrets vào annotation source và trông chờ retention SOURCE sẽ “giấu” được dữ liệu trong hệ quản lý source. Metadata cần được kiểm soát từ **nguồn tạo, nơi lưu tới nơi hiển thị**.

Nếu adapter ghi toàn bộ annotation values vào log, nó có thể vô tình tiết lộ dữ liệu cấu hình. Shared handler nên nhận chỉ những metadata đã được allowlist/redact theo chính sách.

## 37. Các lỗi thường gặp

**Lỗi 1 — Không khai báo @Retention khi cần Reflection.** Default là CLASS; method.getAnnotation trả null.

**Lỗi 2 — Nhầm @Target với @Retention.** METHOD không có nghĩa RUNTIME.

**Lỗi 3 — Class annotation được hỏi qua Method.** Phải chọn đúng AnnotatedElement.

**Lỗi 4 — Type-use annotation được hỏi qua Method.getAnnotations.** Cần AnnotatedType tương ứng.

**Lỗi 5 — Dùng getAnnotation cho repeatables.** Có thể không đọc đủ các annotations gián tiếp qua container.

**Lỗi 6 — Cho rằng @Inherited áp dụng cho method overrides.** Không có quy tắc tự động như vậy.

**Lỗi 7 — Annotation trên interface method nhưng chỉ hỏi implementation Method.** Cần resolution strategy nếu hợp đồng muốn interface inheritance.

**Lỗi 8 — Chọn sai method khi có overload.** Tên không đủ; cần parameter types/descriptors.

**Lỗi 9 — Không xét bridge/synthetic methods.** Pointcut có thể chọn nhầm invocation hoặc metadata lookup lệch.

**Lỗi 10 — Tin rằng có annotation là engine có join point tương ứng.** Metadata visibility và interception coverage khác nhau.

**Lỗi 11 — Cho rằng CLASS annotation không ai đọc được.** Reflection không trả, nhưng class-file reader có thể đọc.

**Lỗi 12 — Cho rằng SOURCE annotation phải xuất hiện trong .class.** Nó thường bị loại ở compilation.

**Lỗi 13 — Không truyền -parameters nhưng muốn dùng Parameter.getName.** Có thể nhận arg0 thay tên thật.

**Lỗi 14 — Dùng cache chỉ theo tên class.** Không xử lý class loaders/versioning.

**Lỗi 15 — Tạo index ở build time rồi không làm mới.** Stale index không phản ánh binary hiện tại.

**Lỗi 16 — Log toàn bộ metadata.** Có thể lộ thông tin và làm tăng overhead.

**Lỗi 17 — Hiểu @Inherited như copy vào subclass bytecode.** Nó là semantics khi tra cứu class-level annotation.

**Lỗi 18 — Nhầm compiled class metadata với runtime arguments.** Annotation không thay thế data context của một lần gọi.

## 38. Ma trận kiểm thử annotation-based AOP

| Ca thử | Cần xác minh |
|---|---|
| SOURCE annotation trên method | Reflection không thấy; compile-time tool có thể xử lý |
| CLASS annotation trên method | Có thể tồn tại trong binary, Reflection không thấy |
| RUNTIME annotation trên method | Reflection trên đúng Method thấy |
| RUNTIME annotation trên class | Reflection trên đúng Class thấy |
| @Retention không khai báo | Mặc định CLASS |
| @Target không cho phép vị trí | Compiler từ chối sử dụng sai |
| @Inherited trên TYPE | Class subclass lookup theo hợp đồng |
| getDeclaredAnnotation trên subclass | Không tự trả annotation chỉ có ở superclass |
| Override method không gắn annotation | Không được coi mặc định là declared annotated |
| Annotation trên interface method | Lookup strategy đúng |
| Repeatable annotation | getAnnotationsByType trả đúng các phần tử |
| Parameter annotation | Parameter đúng index có metadata |
| Parameter name | -parameters semantics đúng |
| Return TYPE_USE annotation | AnnotatedReturnType đúng |
| Two overloaded methods | Không trộn annotations |
| Bridge/synthetic methods | Resolution không tính sai |
| Proxy method khác implementation | Matching được đặc tả |
| Class loader khác | Không dựa tên String đơn lẻ |
| Runtime cache | Không giữ stale metadata |
| Build-time index | Trùng phiên bản binary |
| Weaving scope | Chỉ các classes/joins được hỗ trợ mới có advice |

### 38.1. Hai tầng test cần tách

**Metadata test:** dùng Reflection hoặc class-file tool để chứng minh annotation lưu ở đâu, API đọc thế nào.

**AOP integration test:** chứng minh engine thực sự chọn join point và chạy advice tại ranh giới dự kiến.

Một test Reflection pass không thay thế integration test; một advice không chạy chưa đủ khẳng định retention sai.

## 39. Quy trình chẩn đoán “annotation không được nhận diện”

1. Ghi chính xác annotation type và retention declaration.
2. Nếu không có @Retention, nhớ default CLASS.
3. Kiểm tra @Target tương thích nơi sử dụng.
4. Xác định annotation gắn lên TYPE/METHOD/PARAMETER/TYPE_USE nào.
5. Kiểm tra compilation/source đang dùng trong build hiện tại.
6. Nếu nghi vấn binary, xem class file bằng công cụ phù hợp.
7. Nếu runtime Reflection, thử query đúng AnnotatedElement.
8. Kiểm tra getAnnotation và getDeclaredAnnotation khác nhau thế nào.
9. Nếu repeatable, dùng getAnnotationsByType.
10. Nếu override, kiểm tra superclass/interface lookup policy.
11. Nếu generic, xem bridge/synthetic Method.
12. Nếu proxy, xác định Method từ interface hay implementation.
13. Kiểm tra class loader và module boundary.
14. Xác định engine có hỗ trợ join point cần chọn không.
15. Kiểm tra weaving đã áp dụng đúng class/time phase.
16. Nếu dùng metadata index, kiểm tra version và freshness.
17. Viết regression tests cho các đường ghi nhận và không ghi nhận.
18. Kiểm tra cache key/cached negative lookup khi build/runtime thay đổi.

Không vội đổi RetentionPolicy thành RUNTIME cho mọi annotation khi chưa biết consumer là compile-time tool hay binary weaver. Cần chọn đúng **nhu cầu đọc và giai đoạn đọc**.

## 40. Thiết kế annotation cho một module AOP có thể tái sử dụng

Một thiết kế annotation dùng cho runtime advice nên tự trả lời:

- Annotation này đánh dấu *loại phần tử nào*?
- Có cần runtime Reflection đọc không?
- Nó có thể lặp lại hay không?
- Giá trị mặc định của từng element là gì?
- Override method có được kế thừa logic hay không?
- Interface annotation được xử lý theo quy tắc nào?
- Có được đặt làm meta-annotation cho annotation khác không?
- Nếu có nhiều rules mâu thuẫn, precedence nào được áp dụng?
- Điểm can thiệp là call, execution, constructor hay loại khác?
- Có giới hạn package/class-loading nào không?
- Có thể cache resolution một lần theo class/version không?
- Có dữ liệu nhạy cảm nào không được đưa vào annotation values?

Một annotation tốt không chỉ biên dịch được; **hợp đồng diễn giải metadata cũng cần rõ** để advice behavior nhất quán qua nhiều modules và hình thức can thiệp.

## 41. Khi nào nên dùng metadata thay vì điều kiện runtime?

Metadata tĩnh phù hợp với những thông tin thay đổi rất ít theo invocation, như “method thuộc loại thao tác cần audit” hoặc “operation có category X”.

Điều kiện runtime phù hợp với dữ liệu mỗi lần gọi, như **giá trị tham số hiện tại**, actor đã xác thực hoặc kết quả tính toán.

Một aspect có thể kết hợp:

~~~text
Static selection:
    method is marked for observation

Runtime decision:
    current invocation has approved context
    and event policy allows emission
~~~

Nếu dữ liệu actor/tenant khác nhau theo request, không nên nhúng chúng như annotation values tĩnh trên method. Điều này vừa sai nghĩa vừa dễ rò thông tin qua metadata.

## 42. Một số câu hỏi tự kiểm tra

### Câu 1 — Retention mặc định

Annotation type không có @Retention. Reflection runtime có chắc đọc được không?

**Đáp án:** không. Mặc định retention CLASS, không phải RUNTIME.

### Câu 2 — Có trong class file nhưng không Reflection

Một annotation có retention CLASS. Có thể được binary weaver đọc không, và Method.getAnnotation có thấy không?

**Đáp án:** công cụ đọc class-file annotation attributes có thể thấy; Reflection runtime thông thường không trả annotation ấy.

### Câu 3 — Annotation trên kiểu trả về

@NonEmpty chỉ cho TYPE_USE và đặt trên return type. Nên hỏi Method.getAnnotation hay getAnnotatedReturnType?

**Đáp án:** getAnnotatedReturnType và API annotation trên AnnotatedType tương ứng.

### Câu 4 — Method override

Base.action có @Audit, Child.action override không khai báo annotation. @Inherited có tự copy annotation xuống Child.action không?

**Đáp án:** không. @Inherited áp dụng cho annotation on class trong phạm vi đặc tả, không tự cho method overrides.

### Câu 5 — Repeatables

Hai @Rule gắn cùng method. API nào phù hợp để lấy cả hai?

**Đáp án:** getAnnotationsByType(Rule.class), theo semantics repeatable.

### Câu 6 — Proxy

Interface method có annotation, implementation method không. Reflection trên implementation có tự thấy annotation của interface không?

**Đáp án:** không theo quy tắc tự động chung. Cần lookup policy hoặc khả năng được công cụ xác định.

### Câu 7 — SOURCE retention

Annotation SOURCE có thể có ích cho compile-time analysis không?

**Đáp án:** có, nếu compiler/processor thực sự có annotation ở giai đoạn xử lý thích hợp.

### Câu 8 — Parameter names

Biên dịch không có -parameters, Parameter.getName có chắc trả tên source thật không?

**Đáp án:** không. Có thể trả tên tổng hợp; phải kiểm tra isNamePresent.

### Câu 9 — Annotation type class loader

Hai annotation types cùng tên nhưng class loaders khác nhau có chắc là một type runtime không?

**Đáp án:** không. Type identity có class loader.

### Câu 10 — Cấu trúc và advice

Một method có @Observed và Reflection tìm thấy nó. Có chắc AOP engine sẽ advice method không?

**Đáp án:** không. Cần pointcut matching, weaving/interception coverage và runtime call path phù hợp.

### Câu 11 — Cache theo tên method

Nếu class có work(int) và work(String), cache annotation chỉ theo tên work có an toàn không?

**Đáp án:** không. Phải phân biệt chữ ký/descriptor.

### Câu 12 — Build-time index

Annotation index được tạo từ binary cũ, runtime nạp binary mới. Nguy cơ?

**Đáp án:** stale metadata có thể làm sai selection; cần versioning/rebuild/invalidation.

## 43. Checklist trước khi dùng annotation-based pointcut

- [ ] Đã khai báo đúng @Retention chưa?
- [ ] Consumer cần SOURCE, CLASS hay RUNTIME?
- [ ] @Target có đúng với vị trí thật sự dùng?
- [ ] Đọc đúng Class, Method, Field, Parameter hoặc AnnotatedType?
- [ ] Có xem xét @Inherited đúng giới hạn class-level?
- [ ] Có xử lý repeatable annotations với API phù hợp?
- [ ] Có quy tắc rõ cho interface và overriding?
- [ ] Có xét bridge/synthetic methods khi dùng generics?
- [ ] Đã phân biệt method call và method execution?
- [ ] Có xác minh class-file/weaving/runtime phase?
- [ ] Có xử lý đúng proxy và target method identity?
- [ ] Có class-loader-aware metadata lookup/cache?
- [ ] Có xử lý parameter names theo compiler flags?
- [ ] Có giới hạn metadata values nhạy cảm?
- [ ] Có tests riêng cho Reflection và AOP integration?
- [ ] Có test âm với annotation không tồn tại/retention thấp?
- [ ] Có test behavior khi nhiều annotations/giá trị mâu thuẫn?
- [ ] Có tài liệu hóa điều kiện và giới hạn của metadata matching?

## 44. Tổng kết

**Phạm vi lưu giữ** quyết định annotation còn tồn tại đến giai đoạn nào; **vị trí áp dụng** quyết định annotation gắn vào phần tử nào; **cơ chế đọc** quyết định công cụ có truy cập được metadata hay không.

Các nguyên tắc quan trọng nhất:

- **SOURCE:** hữu ích ở compile time, không còn như runtime annotation trong class file.
- **CLASS:** được giữ trong class file nhưng không hiển thị với Java Reflection thông thường.
- **RUNTIME:** cho phép Reflection truy vấn ở runtime khi chọn đúng phần tử.
- **Không có @Retention nghĩa là CLASS mặc định.**
- **@Target khác @Retention**, và METHOD khác TYPE_USE/PARAMETER.
- **@Inherited chỉ hỗ trợ ngữ nghĩa class-level cụ thể**, không tự sao chép annotation sang method override.
- **Repeatable annotations có container**, nên dùng API truy vấn đúng.
- **Interface, implementation, bridge, proxy và class loader** có thể làm metadata lookup khác nhau.
- **Annotation hiện diện không tự kích hoạt advice**; còn cần khả năng chọn/can thiệp join point.
- **Metadata tĩnh khác context động**, đặc biệt với request arguments và actor.
- **Reflection test và AOP integration test** bổ sung cho nhau, không thay thế nhau.

**Câu hỏi cuối cùng khi thiết kế một annotation-based aspect:** *Annotation được lưu tới đâu, tôi đang hỏi đúng phần tử và loại metadata nào, và cơ chế AOP thực tế có sử dụng được kết quả ấy tại đúng ranh giới can thiệp hay không?*


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="annotation-metadata-005">Kế thừa annotation và khác biệt giữa khai báo với thực thi</a>

<details>
<summary>Xem chi tiết</summary>

# Kế thừa annotation và khác biệt giữa khai báo với thực thi trong Aspect/AOP

## 1. Vì sao chủ đề này quan trọng?

Trong **Aspect-Oriented Programming (AOP)**, một **aspect** có thể dùng annotation làm tiêu chí chọn những **join point** sẽ được advice. Ví dụ người phát triển đánh dấu một method bằng `@Audited` rồi kỳ vọng thao tác ấy sẽ được ghi audit:

```java
@Audited
public void submit() {
    // nghiệp vụ
}
```

Khi ứng dụng có kế thừa, triển khai interface hoặc method overriding, kỳ vọng này dễ sai:

- Annotation ở **class cha** có tự xuất hiện ở class con không?
- Annotation ở **method cha** có tự xuất hiện ở method override không?
- Nếu class con **không override** method, reflection thấy method thuộc class nào?
- Annotation trên **interface method** có tự áp cho implementation method không?
- Một method call qua kiểu interface và một method execution ở implementation có cùng metadata không?
- Nếu runtime receiver mang annotation kiểu, nó có nghĩa **method được gọi** cũng có annotation không?
- Khi proxy lấy một `Method` từ interface, liệu advice có đọc đúng annotation ở implementation?
- Việc một pointcut có thể chọn annotation ở **khai báo** đồng nghĩa advice chắc chắn **chạy** hay chưa?

Các câu hỏi ấy nằm tại giao điểm của **hệ thống kiểu Java**, **quy tắc kế thừa metadata**, **reflection** và **cơ chế chọn join point của một AOP engine**.

Điểm then chốt:

> **Kế thừa hành vi không đồng nghĩa kế thừa annotation; có annotation trong source cũng không đồng nghĩa mọi lời gọi runtime đều được can thiệp.**

Bài này trình bày kiến thức Aspect/AOP tổng quát từ cơ bản đến nâng cao, lấy **Java 17** làm nền tảng ngôn ngữ và **AspectJ** làm một ví dụ cụ thể về cú pháp pointcut dựa trên annotation. Cú pháp của AspectJ không phải cú pháp bắt buộc hay mặc định của mọi hệ thống AOP.

## 2. Mục tiêu học tập

Sau bài này, bạn có thể:

1. Phân biệt **annotation declaration**, **class inheritance**, **method inheritance** và **overriding**.
2. Giải thích chính xác phạm vi của meta-annotation `@Inherited` trong Java.
3. Phân biệt **method được thừa kế** với **method được override**, kể cả khi cả hai có cùng tên.
4. Hiểu khác biệt giữa `getMethod`, `getDeclaredMethod`, `getAnnotation` và `getDeclaredAnnotation`.
5. Phân biệt metadata ở **interface declaration**, **implementation declaration**, **runtime class** và **join-point subject**.
6. Phân biệt method **call** với method **execution** khi chọn annotation.
7. Thiết kế pointcut phù hợp khi class/method/interface có annotation ở những vị trí khác nhau.
8. Xác định ảnh hưởng của retention policy, annotation target, repeatable annotations và metadata ở parameter/type use.
9. Không suy diễn từ pointcut matching thành bảo đảm runtime interception khi còn weaving/proxy boundaries.
10. Kiểm thử dương/âm để xác nhận chính xác coverage và hành vi của advice.

## 3. Annotation trong Java là metadata, không phải lệnh thực thi

Một annotation là metadata gắn với thành phần chương trình. Ví dụ:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Audited {}
```

Annotation `@Audited` tự nó **không chạy một dòng audit nào**. Cần một thành phần khác — compiler, processor, runtime reflection, interceptor hoặc AOP weaver — **đọc** metadata và quyết định hành vi.

Cần tách ba pha:

```text
Source declaration
  @Audited void submit()
        |
        v
Annotation metadata trong source / class file / reflection
        |
        v
AOP pointcut hoặc interceptor đọc/chọn metadata
        |
        v
Advice thực sự được áp dụng tại một join point
```

Bước cuối chỉ xảy ra khi công cụ hỗ trợ join-point kind ấy, class hoặc call site nằm trong phạm vi xử lý, điều kiện động đúng và đường chạy thực tế đi qua interception boundary.

**Annotation không tự biến method thành advised operation.**

## 4. Ba retention policies và vì sao chúng quan trọng

Java hỗ trợ:

| Retention | Có trong source? | Có trong class file? | Java reflection runtime thông thường đọc được? |
| --- | --- | --- | --- |
| `RetentionPolicy.SOURCE` | Có | Không được giữ | Không |
| `RetentionPolicy.CLASS` | Có | Có | Không qua reflection annotation thông thường |
| `RetentionPolicy.RUNTIME` | Có | Có | Có |

Nếu không chỉ định `@Retention`, mặc định của annotation type là **CLASS**.

Ý nghĩa với AOP:

- **Compile-time weaver** có thể quan sát annotation theo cơ chế compiler/source hoặc class file hỗ trợ.
- Một số **bytecode weaver** có thể phân tích annotation retention CLASS hoặc RUNTIME.
- Một **runtime reflection-based interceptor** thường cần `RUNTIME`.
- Đối với **annotation binding** vào tham số advice tại runtime, công cụ có thể yêu cầu `RUNTIME`.
- `SOURCE` không tồn tại trong binary để một runtime-only interceptor đọc lại.

Không có nguyên tắc “cứ dùng AOP thì annotation phải RUNTIME” cho mọi trường hợp; cần xác định **pha thực sự đọc metadata**.

### 4.1. Annotation target khác retention

```java
@Target(ElementType.TYPE)
```

giới hạn nơi có thể **khai báo annotation**, không nói nó tồn tại bao lâu.

```java
@Target(ElementType.METHOD)
```

cho phép đặt lên **method declaration**, không tự áp lên các method được gọi trong class.

```java
@Target(ElementType.TYPE_USE)
```

cho phép đặt trên **vị trí sử dụng kiểu**, khác với annotation của một method hoặc class declaration. Pointcut phải được thiết kế theo đúng loại metadata cần chọn.

## 5. `@Inherited` trong Java: phạm vi rất hẹp

Meta-annotation `java.lang.annotation.Inherited` báo rằng annotation có thể được tìm trên **superclass** khi truy vấn annotation của một **class declaration**.

Ví dụ:

```java
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface Monitored {}

@Monitored
class Parent {}

class Child extends Parent {}
```

Với reflection:

```java
Child.class.isAnnotationPresent(Monitored.class) // true
```

nếu `Child` không trực tiếp khai báo annotation cùng kiểu và chuỗi superclass có annotation phù hợp.

Nhưng:

- `@Inherited` **không truyền annotation từ interface sang class implements**.
- `@Inherited` **không truyền annotation của method được override**.
- `@Inherited` **không truyền annotation của field hoặc constructor**.
- Việc subclass có annotation theo quy tắc này không làm các method của nó tự có method annotation tương ứng.
- `@Inherited` không tự chạy advice, không tự tạo pointcut hay tự sửa bytecode.

**Câu ngắn để ghi nhớ: `@Inherited` là quy tắc tìm annotation ở class hierarchy, không phải quy tắc “mọi thứ kế thừa mọi annotation”.**

### 5.1. Kế thừa class annotation không đồng nghĩa khai báo lại

Với `Child` ở trên:

```java
Child.class.getAnnotation(Monitored.class)         // khác null
Child.class.getDeclaredAnnotation(Monitored.class) // null
```

Hai phép đọc mô tả hai ý nghĩa khác nhau:

- **getAnnotation:** annotation được xem là *present* theo quy tắc của `AnnotatedElement`, có xét superclass với type annotation `@Inherited` phù hợp.
- **getDeclaredAnnotation:** chỉ annotation **khai báo trực tiếp** trên element được hỏi.

Dù `getAnnotation` trên `Child` tìm thấy `Monitored`, mã nguồn `Child` vẫn không chứa một khai báo `@Monitored` trực tiếp.

### 5.2. Class con khai báo annotation riêng

Nếu `Child` trực tiếp gắn cùng annotation type với giá trị khác, giá trị trên class con có thể **che** giá trị inherited của superclass theo semantics tương ứng.

Ví dụ:

```java
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface Level {
    int value();
}

@Level(1)
class Base {}

@Level(2)
class Derived extends Base {}
```

`Derived.class.getAnnotation(Level.class).value()` trả `2`. Đừng tự gộp `1` và `2` thành một danh sách nếu annotation type không được thiết kế để biểu đạt aggregation.

## 6. Interface annotations không được thừa kế theo `@Inherited`

Ví dụ:

```java
@Monitored
interface Task {}

class TaskImpl implements Task {}
```

Ngay cả khi `Monitored` được khai báo `@Inherited`, Java không coi `TaskImpl` có annotation `Monitored` chỉ vì implement `Task`.

```java
TaskImpl.class.isAnnotationPresent(Monitored.class) // false
```

Lý do: quy tắc `@Inherited` của Java tra **superclass chain**, không tìm qua tất cả implemented interfaces.

### 6.1. Nhưng một công cụ có thể có chính sách mở rộng riêng

Một hệ thống metadata hoặc AOP engine có thể chủ động duyệt:

```text
class -> superclass
      -> interfaces
      -> superinterfaces
```

và tự hợp nhất annotation theo một policy. Nếu nó làm vậy, đó là **quy tắc resolver của công cụ**, không phải semantics mặc định của `Class.getAnnotation`.

Phải công bố:

- Có duyệt interfaces không?
- Nếu hai interfaces có annotation cùng type nhưng giá trị xung đột thì chọn thế nào?
- Có ưu tiên class trực tiếp hơn interface không?
- Có duyệt superinterfaces không?
- Có dừng khi gặp annotation trực tiếp không?

Không được suy từ một công cụ tự hỗ trợ interface lookup thành quy tắc chung của Java/AOP.

## 7. Method được thừa kế và method được override là hai trường hợp khác

Giả sử:

```java
class Parent {
    @Audited
    public void original() {}

    @Audited
    public void overridden() {}
}

class Child extends Parent {
    @Override
    public void overridden() {}
}
```

Có hai trường hợp:

**Trường hợp A — method không override**

`Child` không khai báo `original()`. Khi tra public method được thừa kế, reflection có thể trả về chính `Method` được khai báo ở `Parent`. Annotation `@Audited` thuộc **declaration của Parent.original** nên vẫn tồn tại trên Method đó.

**Trường hợp B — method override**

`Child.overridden()` là một **method declaration mới**. Dù nó override behavior của `Parent.overridden()`, annotation `@Audited` **không tự được copy** sang method declaration mới.

```text
Parent.original [@Audited]
       |
Child inherits unchanged
       -> reflection Method declaringClass = Parent
       -> annotation on that Method = present

Parent.overridden [@Audited]
       |
Child.overridden [no @Audited]
       -> reflection Method declaringClass = Child
       -> annotation on Child Method = absent
```

Đây là khác biệt trung tâm của bài.

### 7.1. `@Inherited` không sửa quy tắc này

Kể cả một annotation type đặt `@Inherited`, **method annotation vẫn không được inherited theo override**. Meta-annotation ấy được định nghĩa cho **class-level inheritance**.

Nếu muốn override tiếp tục được AOP advice áp dụng dựa trên annotation cha, phải thiết kế một **chính sách tìm metadata ancestor method** hoặc pointcut type/signature khác; không chỉ thêm `@Inherited`.

### 7.2. Overriding không đồng nghĩa redeclaration của annotation

`@Override` chỉ giúp compiler kiểm tra quan hệ override. Nó không chép metadata runtime của method cha sang method con.

Một method child có thể có **khác hẳn annotation**, kể cả khi trả cùng result. Cần phân biệt **behavioral substitutability** với **metadata inheritance**.

## 8. Reflection: `getMethod` và `getDeclaredMethod`

Với class `Child`:

- `getMethod("name", ...)` tìm **public method**, kể cả public method kế thừa.
- `getDeclaredMethod("name", ...)` chỉ tìm method **khai báo trực tiếp trong class**, bất kể visibility (nhưng cần quyền truy cập phù hợp nếu invoke).
- `Method.getDeclaringClass()` cho biết method declaration thực sự thuộc type nào.
- `Method.getAnnotation(...)` đọc annotation gắn với **method element ấy**, không tự đi lên override chain.
- `Class.getAnnotation(...)` có thể áp `@Inherited` đối với annotation trên class.

Ví dụ:

```java
Method inherited = Child.class.getMethod("original");
Method override = Child.class.getDeclaredMethod("overridden");

System.out.println(inherited.getDeclaringClass()); // Parent
System.out.println(override.getDeclaringClass());  // Child
```

Đây là **mảnh mã minh họa**; chương trình Java 17 runnable đầy đủ ở phần tiếp theo.

### 8.1. Khi dùng `getMethods()`

`getMethods()` trả một tập public methods nhìn thấy qua class, bao gồm inherited public methods. Kết quả không đồng nghĩa “mọi method được khai báo trong class”. Không dùng nó để kết luận `@Audited` được khai báo trực tiếp trên một class.

### 8.2. Khi dùng `getDeclaredMethods()`

`getDeclaredMethods()` không liệt kê những inherited methods chỉ tồn tại ở superclass/interface. Nếu aspect/interceptor dùng API này mà chưa xét inheritance, nó có thể bỏ sót annotation trên inherited declarations.

### 8.3. Metadata của method không phụ thuộc runtime receiver trong reflection thông thường

Một `Method` object chỉ một **declaration** cụ thể. `method.getAnnotation(Audited.class)` không thay đổi chỉ vì ta invoke method đó trên object kiểu `Parent` hay `Child`.

Ngược lại, **dispatch khi invoke** có thể chọn implementation override của receiver. Đây là ranh giới khai báo/thực thi rất quan trọng.

## 9. Java 17 runnable: class annotation, method override, interface và dispatch

Chương trình sau không cần một AOP framework hoặc compiler đặc biệt. Nó dùng reflection và một interceptor tự viết để minh họa chính xác **metadata có thể khác hành vi thực sự chạy**.

Tạo file `AnnotationInheritanceDemo.java`:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

public class AnnotationInheritanceDemo {

    @Inherited
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface Monitored {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Audited {}

    @Monitored
    static class Parent {
        @Audited
        public String process() {
            System.out.println("business:parent-process");
            return "parent";
        }

        @Audited
        public String inherited() {
            System.out.println("business:inherited");
            return "inherited";
        }
    }

    static class Child extends Parent {
        @Override
        public String process() {
            System.out.println("business:child-process");
            return "child";
        }
    }

    @Monitored
    interface Tagged {}

    static class TaggedImpl implements Tagged {}

    interface Task {
        @Audited
        String apply();
    }

    static class TaskImpl implements Task {
        @Override
        public String apply() {
            System.out.println("business:task");
            return "task";
        }
    }

    static void execute(String label, Object receiver,
                        Method selectedMethod) throws Exception {
        boolean annotated =
                selectedMethod.isAnnotationPresent(Audited.class);

        System.out.println("case:" + label
                + ":declaration="
                + selectedMethod.getDeclaringClass().getSimpleName()
                + ":annotated=" + annotated);

        if (annotated) {
            System.out.println("advice:before:" + label);
        }

        Object result = selectedMethod.invoke(receiver);
        System.out.println("result:" + result);

        if (annotated) {
            System.out.println("advice:after:" + label);
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("class:child:present="
                + Child.class.isAnnotationPresent(Monitored.class));
        System.out.println("class:child:declared="
                + (Child.class.getDeclaredAnnotation(
                        Monitored.class) != null));
        System.out.println("class:interface-impl:present="
                + TaggedImpl.class.isAnnotationPresent(Monitored.class));

        Method childOverride =
                Child.class.getDeclaredMethod("process");
        Method inheritedMethod =
                Child.class.getMethod("inherited");
        Method interfaceMethod =
                Task.class.getMethod("apply");
        Method implementationMethod =
                TaskImpl.class.getMethod("apply");
        Method parentMethod =
                Parent.class.getMethod("process");

        System.out.println("method:child-override:annotated="
                + childOverride.isAnnotationPresent(Audited.class));
        System.out.println("method:inherited:declaring="
                + inheritedMethod.getDeclaringClass().getSimpleName());
        System.out.println("method:inherited:annotated="
                + inheritedMethod.isAnnotationPresent(Audited.class));
        System.out.println("method:interface:annotated="
                + interfaceMethod.isAnnotationPresent(Audited.class));
        System.out.println("method:implementation:annotated="
                + implementationMethod.isAnnotationPresent(Audited.class));

        Child child = new Child();
        TaskImpl task = new TaskImpl();

        execute("parent-method-on-child", child, parentMethod);
        execute("child-override", child, childOverride);
        execute("inherited-method", child, inheritedMethod);
        execute("interface-declaration", task, interfaceMethod);
        execute("implementation-declaration",
                task, implementationMethod);
    }
}
```

Biên dịch/chạy bằng JDK 17 trở lên:

```bash
javac AnnotationInheritanceDemo.java
java AnnotationInheritanceDemo
```

**Stdout dự kiến chính xác:**

```text
class:child:present=true
class:child:declared=false
class:interface-impl:present=false
method:child-override:annotated=false
method:inherited:declaring=Parent
method:inherited:annotated=true
method:interface:annotated=true
method:implementation:annotated=false
case:parent-method-on-child:declaration=Parent:annotated=true
advice:before:parent-method-on-child
business:child-process
result:child
advice:after:parent-method-on-child
case:child-override:declaration=Child:annotated=false
business:child-process
result:child
case:inherited-method:declaration=Parent:annotated=true
advice:before:inherited-method
business:inherited
result:inherited
advice:after:inherited-method
case:interface-declaration:declaration=Task:annotated=true
advice:before:interface-declaration
business:task
result:task
advice:after:interface-declaration
case:implementation-declaration:declaration=TaskImpl:annotated=false
business:task
result:task
```

### 9.1. Vì sao `Child.class.isAnnotationPresent(Monitored.class)` là true?

`Monitored` được meta-annotated bằng `@Inherited` và được đặt trên `Parent`. `Child` extends `Parent` nhưng không trực tiếp khai báo `Monitored`, nên runtime class reflection tìm theo quy tắc inherited class annotation.

`Child.class.getDeclaredAnnotation(...)` là null vì không có khai báo annotation trực tiếp trên `Child`.

### 9.2. Vì sao `TaggedImpl` không có annotation?

`TaggedImpl` implements `Tagged`, còn `@Inherited` **không tìm annotation qua interface hierarchy**.

Đây là kết quả của **Java reflection semantics**, không phải phán quyết mọi AOP engine có hoặc không có resolver interface metadata riêng.

### 9.3. Vì sao `Child.process` không có `@Audited`?

`Child.process()` là một **override declaration mới**. Method-level annotation từ `Parent.process` không được Java kế thừa tự động.

### 9.4. Vì sao `Child.inherited` vẫn có `@Audited` khi tra `getMethod`?

`Child` không override `inherited`, nên `getMethod("inherited")` trả về `Method` được khai báo ở `Parent`. Annotation tồn tại **trên declaration của Parent**, chứ không phải được chép sang Child.

### 9.5. Vì sao `parent-method-on-child` in ra `business:child-process`?

Interceptor nhận một `Method` object đại diện cho `Parent.process` (có `@Audited`), nhưng gọi nó trên receiver thực tế là `Child`.

Theo dispatch động của Java với instance method override, `Method.invoke(child)` thực thi **Child.process**. Do vậy:

```text
Metadata được đọc từ Parent.process
Actual body chạy là Child.process
```

Chương trình cố ý mô phỏng một **policy chọn metadata dựa trên declaration được truyền vào**, không khẳng định một AOP engine phải chọn như thế ở mọi call/execution join point.

### 9.6. Vì sao interface-declaration có advice nhưng implementation-declaration không?

`Task.apply` được gắn `@Audited`; `TaskImpl.apply` không được gắn. Một interceptor đọc `Method` từ interface sẽ thấy annotation; một interceptor đọc `Method` từ implementation sẽ không thấy.

Cả hai invocation cuối cùng có thể chạy **cùng body TaskImpl.apply**. Sự khác biệt nằm ở **nguồn declaration metadata**, không phải body nghiệp vụ.

### 9.7. Những giới hạn của demo

- Không có weaving hoặc pointcut DSL thực sự.
- Interceptor chỉ kiểm một `Method` object được truyền vào, không tự chọn join point từ ứng dụng.
- Các method trong demo public để reflection invocation không vướng access.
- Reflection `invoke` có thể bọc exception business trong `InvocationTargetException`; ví dụ này không dùng nhánh lỗi.
- Việc source/interface metadata được chọn là **policy minh họa**, không chứng minh một engine khác sẽ làm y hệt.
- Không mô phỏng bridge methods, generics, default methods, concurrency hoặc asynchronous completions.

## 10. “Khai báo” và “thực thi” là hai trục độc lập

Cần phân biệt **declaration site** và **runtime execution site**.

```text
Interface Task.apply() [@Audited]          metadata ở interface
                   |
                   v
        TaskImpl.apply() [không annotation]
                   |
                   v
             Runtime executes TaskImpl.apply
```

Bốn thứ khác nhau:

1. **Annotated declaration:** annotation thực sự đặt trên element nào?
2. **Static signature at call site:** compiler thấy lời gọi qua kiểu nào?
3. **Runtime receiver class:** object thật thuộc lớp nào?
4. **Executed method body:** runtime dispatch vào phần code nào?

Một AOP engine có thể dựa vào một hoặc nhiều thứ ấy tùy **pointcut kind**, **designator** và **weaving boundary**.

### 10.1. Đừng dùng “method có annotation” mà không nêu Method nào

Tối thiểu phải biết: `Task.apply` ở interface, `TaskImpl.apply` ở implementation, hay `Parent.process` ở superclass?

Đó không phải các element tương đương trong Java reflection, dù chúng có quan hệ override/implementation.

## 11. Method call khác method execution trong AOP

Hai join-point kinds có ngữ nghĩa khác nhau trong những mô hình AOP hỗ trợ cả hai:

- **Method call join point:** sự kiện tại **nơi gọi** một method.
- **Method execution join point:** sự kiện tại **thời điểm thân method được thực thi**.

Ví dụ:

```java
Task task = new TaskImpl();
task.apply();
```

Ở source, static type của biến `task` là `Task`, nhưng runtime dispatch chạy `TaskImpl.apply`.

Với một engine hỗ trợ call-site weaving, pointcut dựa annotation ở call signature có thể xét **declaration được call site tham chiếu**.

Với execution weaving, pointcut có thể xét **method execution subject** tương ứng với body implementation.

**Do đó cùng một invocation logic có thể khớp annotation-based call pointcut nhưng không khớp annotation-based execution pointcut**, hoặc ngược lại, tùy metadata đặt ở đâu và quy tắc chính xác của engine.

### 11.1. Java dynamic dispatch không tự sao chép metadata

Dynamic dispatch chọn body thực thi. Nó **không gắn @Audited từ interface/parent lên class implementation/override**. Mọi lựa chọn kế thừa metadata ngoài quy tắc Java phải được diễn đạt bằng một resolver hoặc pointcut policy.

## 12. Cú pháp AspectJ: `@annotation`

Phần này dùng **AspectJ pointcut language** để minh họa. Đây không phải syntax Java thuần hay chuẩn cho mọi công cụ.

```java
pointcut annotatedExecutions():
    execution(* *(..)) && @annotation(Audited);

before(): annotatedExecutions() {
    System.out.println("audited execution");
}
```

**Đây là mảnh code AspectJ**, giả định `Audited` tồn tại với retention/target phù hợp và có một `aspect` chứa các khai báo. Nó chỉ những **execution join point** mà **subject** được xem có annotation `@Audited`.

Điều **không được suy** từ ví dụ:

- Không có quy tắc “override của annotated method cũng annotated”.
- Không có quy tắc “method implementation kế thừa method annotation từ interface”.
- Không có nghĩa chỉ cần đánh dấu interface là execution body implementation tự match.
- Không bảo đảm tất cả class sẽ được woven vào ứng dụng.
- Không bảo đảm annotation retention SOURCE có thể được đọc ở runtime.

### 12.1. `@annotation` trên method call

Một AspectJ snippet khác:

```java
pointcut annotatedCalls():
    call(* *(..)) && @annotation(Audited);
```

Pointcut này lựa chọn **call join points** có annotation ở subject của call, không đồng nghĩa với `execution`. Với interface/override, metadata subject có thể khác phương thức mà JVM cuối cùng chạy.

Tài liệu AspectJ phân biệt **subject of join point** và runtime target; phải kiểm tra đúng loại điểm, chữ ký và static call site.

### 12.2. Pattern có annotation trong method signature

Cũng trong AspectJ:

```java
execution(@Audited * *(..))
```

là ví dụ dùng **annotation pattern trên declaration của method** để chọn execution. Mặc dù có thể diễn tả tập tương tự `@annotation` ở một số trường hợp, binding giá trị annotation hoặc các điều kiện phức tạp cần xem ngữ nghĩa riêng của pointcut.

Đừng nhầm `@Audited` trong pattern với một lời gọi Java method có tên `Audited`.

## 13. `@within` và `@target` không cùng ý nghĩa

AspectJ có nhiều annotation designators:

| Designator | Trọng tâm lựa chọn trong AspectJ |
| --- | --- |
| `@annotation(A)` | Annotation ở **subject** của join point |
| `@within(A)` | Join point nằm trong code được khai báo trong type mang A |
| `@withincode(A)` | Join point nằm trong method/constructor mang A |
| `@this(A)` | Runtime type của object `this` tại join point mang A |
| `@target(A)` | Runtime type của target object mang A |
| `@args(A)` | Annotation trên runtime type của đối số được bind/match |

Các designator có những yêu cầu retention khác nhau. Theo tài liệu AspectJ:

- `@this`, `@target`, `@args` chỉ match khi annotation có **runtime retention**.
- `@within`, `@withincode` và `@annotation` có thể match annotation có ít nhất **class-file retention** theo khả năng weaving; khi cần **bind annotation instance vào advice**, yêu cầu runtime retention.

### 13.1. `@within`: lexical declaring type

Ví dụ:

```java
@within(Monitored) && execution(* *(..))
```

được đọc như các execution join points trong code được khai báo ở type thỏa điều kiện annotation theo ngữ nghĩa AspectJ.

Nó **không có nghĩa mọi method trong type đã có @Audited method annotation**. Đây là tiêu chí của **type declaration** và lexical location.

### 13.2. `@target`: runtime target type

```java
@target(Monitored) && execution(* *(..))
```

nhấn mạnh annotation trên **runtime target type**. Một runtime receiver là subclass của type được đánh dấu `@Inherited` có thể liên quan tới inherited class-level annotation theo quy tắc ngôn ngữ/công cụ, trong khi lexical declaring type lại thuộc type khác.

Không đánh đồng `@within` và `@target`: một bên hỏi **code khai báo trong type nào**, bên kia hỏi **object runtime thuộc type nào**.

### 13.3. `@this` và `@target` khác nhau khi có proxy-like models

Một số AOP implementations phân biệt `this` hiện tại và `target` object. Trong weaving models, binding cụ thể phụ thuộc join-point kind. Không áp một công thức “`this` luôn là proxy” cho mọi AOP engine; phải đọc semantics chính thức của công cụ.

## 14. `@withincode`: annotation ở method *chứa* join point

Ví dụ AspectJ:

```java
call(* *(..)) && @withincode(Audited)
```

Ý tưởng: chọn các **call join points** nằm **bên trong phần code của method/constructor được đánh dấu @Audited**.

Khác với:

```java
call(* *(..)) && @annotation(Audited)
```

Điểm thứ hai quan sát annotation ở **subject của lời gọi**, không phải ở **method đang chứa câu lệnh call**.

Ví dụ:

```java
@Audited
public void process() {
    helper();  // call nằm trong một method được chú thích
}
```

`@withincode(Audited)` có thể chọn call tới `helper` vì call site nằm trong body annotated `process`, **dù `helper` không mang @Audited**.

Đây là một khác biệt giữa **“declaration chứa code đang chạy”** và **“declaration của method được gọi”**.

## 15. Tại sao override làm thay đổi annotation-based pointcut?

Với:

```java
class Parent {
    @Audited
    public void process() {}
}

class Child extends Parent {
    @Override
    public void process() {}
}
```

Trong Java, annotation ở `Parent.process` không tự xuất hiện ở `Child.process`.

Đối với pointcut dựa trên **method execution annotation** của method override:

```text
execute Parent.process -> subject annotated @Audited
execute Child.process  -> child declaration not annotated
```

Khi call site dùng static type `Parent` nhưng runtime receiver là `Child`, call-site selection có thể liên hệ declaration `Parent.process` trong khi body thực tế là `Child.process`.

Điều này lý giải một vấn đề AOP rất phổ biến: **đánh dấu superclass method không luôn đủ để can thiệp mọi override bằng execution annotation pointcut**.

### 15.1. Những cách sửa có chủ đích

- **Gắn lại annotation trên override** nếu mỗi implementation cần xác nhận độc lập.
- **Đặt class-level annotation có @Inherited** rồi chọn theo đúng type-level policy, nếu nghiệp vụ là “toàn bộ type thuộc nhóm được audit”.
- **Viết một resolver duyệt overridden ancestor methods** nếu đó là yêu cầu và engine hỗ trợ.
- **Dùng pointcut theo signature/type/interface/role** thay vì dựa duy nhất method annotation.
- **Sử dụng một wrapper/facade chịu trách nhiệm công khai** để bảo đảm security/audit boundary khi annotation lookup qua proxy có thể mơ hồ.

Không giải quyết bằng cách tự thêm `@Inherited` vào `@Target(METHOD)` rồi kỳ vọng Java tự kế thừa annotation method.

## 16. Interface method annotation: lựa chọn giao diện hay triển khai?

Ví dụ:

```java
interface Billing {
    @Audited
    Receipt charge();
}

class BillingImpl implements Billing {
    @Override
    public Receipt charge() {
        // logic
    }
}
```

Annotation `@Audited` nằm ở `Billing.charge`. `BillingImpl.charge` là **method declaration riêng** không tự có annotation.

Các cách định nghĩa chính sách:

**Policy A — theo interface contract:** mọi implementation của `Billing.charge` đều được audit vì interface công bố một hợp đồng xuyên suốt.

**Policy B — theo implementation opt-in:** chỉ những concrete implementation methods gắn `@Audited` được audit.

**Policy C — theo public entry point:** audit khi caller đi qua một facade/proxy cụ thể, không phụ thuộc annotation kế thừa.

Cả ba đều có thể hợp lý tùy mục tiêu, nhưng **không phải cùng một policy**.

### 16.1. Tránh phụ thuộc cách interceptor lấy `Method`

Một dynamic proxy interface có thể nhận metadata method tương ứng **interface**; một mechanism khác có thể nhìn implementation method. Nếu code giả định luôn lấy đúng nguồn nhưng deployment đổi interception strategy, coverage có thể thay đổi.

Hợp đồng AOP nên nêu rõ annotation được đọc ở đâu và có fallback từ implementation sang interface không.

### 16.2. Nếu interface có nhiều cha

```text
Interface A declares work @Policy(level=1)
Interface B declares work @Policy(level=2)
Concrete C implements A, B
```

Resolver tự duyệt interfaces có thể gặp **xung đột**. Không nên tự chọn “interface đầu tiên” phụ thuộc thứ tự reflection không được hứa.

Cần một precedence policy, quy tắc hợp nhất, hoặc từ chối cấu hình mơ hồ.

## 17. Không nên “hợp nhất annotation” theo trực giác

Một số hệ thống cố làm:

```text
Find annotation on:
  1. concrete method
  2. superclass method
  3. interface method
  4. runtime class
  5. superclass class annotation
```

Đây là **cơ chế resolver tùy chỉnh**. Nếu chọn triển khai, phải ghi:

- Thứ tự ưu tiên.
- Dừng ở annotation đầu tiên hay hợp nhất nhiều annotation?
- Thế nào là override hợp lệ? Bao gồm covariant return, generics?
- Có tính bridge/synthetic methods không?
- Có duyệt superinterfaces không?
- Cách giải quyết diamond graph.
- Annotation repeatable được trả dưới dạng nào?
- Annotation trên type khác annotation trên method ra sao?
- Nếu annotation âm tính hoặc override policy, điều gì thắng?
- Có cache metadata để tránh reflection lặp nhiều lần không?

**Đừng tự biến một thuật toán resolver riêng thành “Java annotation inheritance”.** Ghi rõ đây là semantics của thư viện hoặc application AOP policy.

## 18. Declaration metadata và runtime context phải được đánh giá riêng

Ví dụ một annotation trên class:

```java
@Monitored
class SharedService {}
```

Một runtime object `SharedService` có type metadata. Nhưng điều đó không nói rằng mọi method đều có annotation `@Audited`.

Một annotation trên method:

```java
@Audited
public void save() {}
```

không nói rằng runtime args, target hay caller mang `@Monitored`.

Vì thế khi thiết kế aspect nên tách:

1. **Type-level selection** — dựa trên class/interface metadata theo policy.
2. **Method-level selection** — dựa trên method declaration metadata.
3. **Argument-level dynamic checks** — dựa trên actual argument values/runtime types.
4. **Control-flow selection** — dựa trên ngữ cảnh call stack/join-point flow.
5. **Weaving coverage** — class nào/call site nào thực sự được instrument.

Một “chính sách @Audited” có thể là kết hợp của nhiều tiêu chí nhưng phải diễn đạt rõ phép AND/OR/exclude.

## 19. `@Inherited` và repeatable annotations

Java cho phép một annotation type được khai báo `@Repeatable`. Khi tra annotation bằng reflection:

- `getAnnotation(A.class)` có semantics cho **một annotation type**; không tự trả mọi repeated annotations.
- `getAnnotationsByType(A.class)` có thể **mở container annotation** để trả nhiều annotation type A.
- `getDeclaredAnnotationsByType(A.class)` chỉ xét annotation trực tiếp/gián tiếp trên element, **không kế thừa**.
- `getAnnotationsByType` trên class có thể xét `@Inherited` theo quy tắc *associated* annotations.
- Quy tắc với **container annotation** phải phù hợp với yêu cầu của `@Repeatable` và `@Inherited` trong JLS.

Ví dụ cấu trúc:

```java
@Inherited
@Repeatable(Labels.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface Label {
    String value();
}

@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface Labels {
    Label[] value();
}
```

Đây là **mảnh code Java** để giải thích schema repeatable; compiler cần thấy import `java.lang.annotation.*` và các khai báo đặt trong file hợp lệ. Có thể khai báo cả repeatable type và container với `@Inherited` nếu muốn thực hiện kế thừa phù hợp.

### 19.1. Repeatable + subclass annotations không phải hợp nhất tự động

Nếu superclass có hai `@Label` và subclass có một `@Label` khác, không tự suy danh sách của subclass là **ba**. Theo quy tắc liên quan đến `getAnnotationsByType`, khi class hiện tại có annotation liên quan trực tiếp/gián tiếp, kết quả có thể ưu tiên declaration hiện tại thay vì cộng dồn superclass.

Một AOP resolver cần định nghĩa rõ muốn **shadow**, **append** hay **reject conflicts**.

## 20. Annotation trên parameter và type use khác method annotation

Ví dụ:

```java
public void save(@Sensitive String token) {}
```

`@Sensitive` trên **parameter declaration** (nếu `@Target(PARAMETER)`) không có nghĩa method `save` được annotate `@Sensitive`.

Ví dụ khác:

```java
public @Nonnull String lookup() { ... }
```

`@Nonnull` có thể là annotation trên **return type use**, không nhất thiết là method declaration annotation, tùy `@Target` được hỗ trợ.

Java reflection phân biệt:

- `Method.getAnnotations()` cho declaration annotations của method.
- `Method.getParameterAnnotations()` cho annotations trên parameter declarations.
- `Method.getAnnotatedReturnType()` cho type-use annotations của return type.
- `Parameter.getAnnotations()` cho parameter declaration annotation.
- `AnnotatedType` API cho metadata gắn với type uses.

AOP engines có thể hỗ trợ matching parameter annotations/type patterns theo cú pháp riêng. Không coi `@annotation(A)` là một phép tìm mọi annotation nằm ở bất kỳ vị trí nào trong chữ ký method.

## 21. Bridge và synthetic methods khi có generics

Java compiler có thể tạo **bridge methods** để giữ polymorphism sau type erasure. Một class có:

```java
interface Store<T> {
    T load();
}

class StringStore implements Store<String> {
    public String load() {
        return "value";
    }
}
```

có thể có bridge method synthetic ở class file để thực hiện hợp đồng erased signature.

Rủi ro khi xây annotation resolver:

- Reflection liệt kê nhiều `Method` có vẻ cùng tên.
- Annotation có thể hiện diện/được copy lên bridge method tùy cơ chế compiler, không được suy tất cả toolchains đều giống nhau.
- Pointcut signature/annotation matching có thể phụ thuộc compiler/weaver.
- Cache key chỉ dùng tên method dễ trùng overload/bridge.
- Một method đang chạy không đồng nhất với element mà annotation ban đầu được đặt trong source.

Khi thiết kế AOP dùng annotation làm quy tắc quan trọng, nên kiểm thử bytecode và `Method.isBridge()`, `Method.isSynthetic()`, signature thực sự với compiler/toolchain đang dùng.

### 21.1. Không dùng tên method làm khóa metadata duy nhất

`save(String)` và `save(long)` có thể mang policy khác. Resolver nên định danh method bằng declaring type, tên, parameter types và các thông tin phù hợp; đừng bỏ qua overload/generic erasure.

## 22. Default methods và abstract methods của interface

Một interface có thể có **default method** với implementation. Khi class không override, phần code được thực thi có thể nằm trong interface default method.

Khác với interface abstract method chỉ công bố contract:

```java
interface AuditOps {
    @Audited
    default void audit() {
        // code mặc định
    }
}
```

Tùy static receiver và weaving model:

- Call site có thể liên hệ method declaration trong interface.
- Execution body có thể là default implementation của interface.
- Nếu class override, body và method declaration thay đổi.
- Reflection `getMethod` có thể trả interface method khi class kế thừa nó.
- Nếu class cụ thể override, annotation trên default method không tự được copy vào override.

Nên có test riêng cho default method thay vì suy từ những case class inheritance thông thường.

## 23. Annotation trên constructor không được kế thừa

Constructors không override như instance methods. Một constructor annotation trên superclass **không xuất hiện** trên constructor của subclass chỉ vì subclass gọi `super(...)`.

```java
class Parent {
    @AuditedConstructor
    Parent() {}
}

class Child extends Parent {
    Child() {
        super();
    }
}
```

Đây là **mảnh code minh họa**, giả định `AuditedConstructor` có target phù hợp. Không có nghĩa `Child()` đã gắn annotation của `Parent()`.

Một aspect chọn **constructor call/execution** phải xem join-point kind, declaration của constructor và weaving scope; không áp quy tắc override method.

## 24. Annotation có thể ở package/module mà không phải type/member

Java cho phép annotation trên package hoặc module declarations theo `@Target(PACKAGE)` hoặc `@Target(MODULE)` và cú pháp khai báo hợp lệ.

Việc một package được annotate **không tự làm mọi class trong package mang annotation**. Tương tự, module metadata không tự lan thành class/method annotations.

Một AOP framework có thể tự xây policy kiểu “mọi class trong package có marker đều được audit”, nhưng đây là **configuration rule riêng**, không phải Java `@Inherited`.

Trong AspectJ cụ thể, các loại annotation pointcut được hỗ trợ và giới hạn với package annotations cần tra **tài liệu phiên bản đang dùng**; không suy rằng một designator nhìn method annotation sẽ tự đọc package metadata.

## 25. Type-level annotation và cấu trúc inheritance phức tạp

Xét:

```text
@Monitored
Base
  |
Middle
  |
Leaf
```

Nếu `@Monitored` là annotation type có `@Inherited` và không bị một declaration trực tiếp phù hợp che, `Leaf.class.getAnnotation(Monitored.class)` có thể tìm xuyên các superclass theo quy tắc Java.

Nhưng nếu:

```text
@Monitored Interface A
          \
           Leaf implements A
```

thì `Leaf` không thừa kế class-level annotation từ interface theo `@Inherited`.

Cần phân biệt:

- **Superclass inheritance path:** là nơi `@Inherited` của Java có hiệu lực.
- **Interface implementation path:** không nằm trong quy tắc ấy.
- **Containment/nesting:** inner class không tự thừa kế annotation của enclosing class.
- **Composition/delegation:** một field giữ object annotated không làm class chứa field mang annotation.
- **Subclass-specific declaration:** có thể che hoặc thay giá trị theo annotation type.
- **Annotation meta-hierarchy:** không có quy tắc Java “tự duyệt mọi annotation gắn trên annotation khác” thành một compositional policy.

## 26. Meta-annotation không đồng nghĩa alias/chaining tùy ý

Một annotation type có thể được annotate bởi annotation khác:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
@interface Classified {}

@Classified
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface Restricted {}
```

Một class gắn `@Restricted` **không tự được `Class.getAnnotation(Classified.class)` xem là có `@Classified`**. `Classified` ở đây được khai báo trên **annotation type Restricted**, không trực tiếp trên class.

Nếu muốn “annotation composed” có thể thay marker khác trong pointcut/resolver, phải có quy tắc xử lý meta-annotation riêng, có thể duyệt annotation types và xác định cách giải quyết alias/conflict.

**Java không tự định nghĩa semantics alias của mọi meta-annotations.**

## 27. Cách thiết kế resolver annotation có chủ đích

Một hệ thống muốn hỗ trợ kế thừa metadata qua interface/override có thể định nghĩa một **hợp đồng**:

```text
ResolvePolicy(method, runtimeClass):
  1. Read annotation directly on most-specific method.
  2. If none, inspect matching overridden superclass declaration.
  3. If none, inspect matching interface declarations.
  4. If none, inspect class-level policy (direct/inherited).
  5. If none, return NO_POLICY.
```

Đây là **pseudocode của một resolver do ứng dụng thiết kế**, không phải semantics mặc định của Java hay pointcut AspectJ.

### 27.1. Kiểm tra “matching overridden declaration” không đơn giản bằng tên

Resolver cần hiểu:

- Method overloads.
- Parameter erasure/bridge.
- Covariant returns.
- Visibility/override constraints.
- Static method hiding (không phải overriding).
- Private methods (không override theo nghĩa thông thường).
- Interface default methods.
- Những class/member ngoài weaving scope.
- Quy tắc dispatch runtime và module access.

Nếu chỉ “tìm method cùng tên trên superclass”, dễ gắn policy của method không liên quan.

### 27.2. Cần chính sách conflict rõ

Nếu concrete method không gắn annotation, còn superclass/interface có hai policy khác nhau, cần biết:

- Ưu tiên class direct annotation không?
- Có inherit policy từ interface nếu override không annotate không?
- Interface A hay B thắng?
- Có nên từ chối khi conflicting values?
- Có annotation “opt-out” riêng không, với semantics gì?
- Có cần cache kết quả không và khi nào invalidate?

Một policy bảo mật hoặc audit bắt buộc không nên dựa trên thứ tự reflection không được bảo đảm.

## 28. Metadata resolution khác weaving coverage

Ngay cả khi resolver trả “có `@Audited`”:

```text
policy resolver says audited = true
```

nó **không đảm bảo advice thực sự chạy** ở mọi invocation.

Các nguyên nhân:

- Class/call site chưa được weave.
- Engine chỉ intercept những interface proxy invocations cụ thể.
- Caller giữ raw target và gọi trực tiếp.
- Target self-invocation không đi qua proxy delegation vào object khác.
- Advice precedence cao hơn short-circuit trước khi advice bên trong chạy.
- Runtime guard hoặc dynamic condition không match.
- Build khác đã dùng metadata đã cũ hoặc class loader khác.
- Method final/private/static/constructor không nằm trong loại join point engine hỗ trợ.
- Annotation retention không tương thích với pha đọc.

Do đó test phải tách:

1. **Metadata present?**
2. **Pointcut matched?**
3. **Actual interception fired?**
4. **Advice behavior satisfied contract?**

Không được gộp thành một unit test `isAnnotationPresent == true` rồi tuyên bố hệ thống AOP an toàn.

## 29. Thực hành với AspectJ: câu hỏi cần trả lời trước khi viết pointcut

Trước một pointcut dựa annotation:

```java
execution(* *(..)) && @annotation(Audited)
```

hãy hỏi:

1. Metadata nằm trên implementation method hay interface method?
2. Method có được override mà quên annotation không?
3. Class-level annotation có `@Inherited` không?
4. Dùng `execution` hay `call` đúng boundary mong muốn?
5. Type/method nào là subject trong DSL của engine?
6. Có dynamic subtype context cần xét `@target` hay không?
7. Có yêu cầu matching annotation ở code chứa join point (`@within`, `@withincode`) không?
8. Annotation retention CLASS/RUNTIME có tương thích không?
9. Có cần bind giá trị annotation vào advice không?
10. Module/class loader nào đã được weave?
11. Có self-invocation hoặc raw-target bypass không?
12. Advice có thể bị precedence/around short-circuit không?

### 29.1. Một mẫu pointcut “có cả type và method”

Có thể muốn:

```text
advised operation = method explicitly @Audited
                    OR execution on a @Monitored target type
```

Đây là **quy tắc nghiệp vụ giả định**, không phải lúc nào cũng đúng. Nếu được triển khai, cần chú ý:

- `OR` có thể tăng coverage mạnh.
- `@Monitored` inherited từ superclass không nói từng method annotation nào tồn tại.
- Một method nhạy cảm được loại bởi exclusion có thể vẫn bị `OR` chọn lại nếu thiếu group parentheses.
- Một target type có runtime annotation nhưng method chạy trong library ngoài scope có thể không được weave.

Nên xác nhận cả **positive và negative matching** trước khi bật.

## 30. Bộ kiểm thử nên có

| Tình huống | Java annotation kết quả | Kiểm thử AOP cần thêm |
| --- | --- | --- |
| Class con extends annotated class với `@Inherited` | Class-level annotation có thể present | Xác nhận type-level pointcut và runtime target |
| Class con implements annotated interface | Không tự inherited qua interface | Xác nhận engine có policy interface traversal hay không |
| Child override annotated parent method | Child override không tự annotated | Xác nhận method-execution pointcut có match không |
| Child kế thừa method không override | `getMethod` có thể trả Parent Method có annotation | Xác nhận execution/call subject |
| Interface method annotated, impl không annotated | Hai `Method` có metadata khác nhau | Test proxy/interceptor nguồn method được chọn |
| Annotation chỉ `CLASS` retention | Java runtime reflection không đọc được | Test compile/binary weaver support |
| Annotation chỉ `SOURCE` retention | Không tồn tại trong class file | Test processor/compiler, không chỉ runtime |
| Repeatable annotations | `getAnnotationsByType` có thể mở container | Test shadow vs merge superclass |
| Generic bridge method | Có thể có synthetic/bridge metadata khác | Test actual selected Method và bridge |
| Interface default method | Body có thể nằm ở interface | Test override và execution boundary |
| Parameter annotation | Không tự là method annotation | Test designator/API đúng loại metadata |
| Type-use annotation | Không tự là class declaration annotation | Test `AnnotatedType`/DSL support |
| Raw target invocation | Có thể bypass proxy | Test advice thực sự chạy |
| Self-invocation | Phụ thuộc weaving/proxy model | Test nested method coverage |
| Multi-aspect overlap | Có thể nhận nhiều advice | Test precedence, event count |
| Async method | Method return khác task completion | Test completion boundary |

### 30.1. Positive test

Một method được đánh dấu trực tiếp tại đúng declaration và nằm trong weaving scope cần nhận advice.

### 30.2. Negative test

Một override không có annotation **không được coi là có method annotation tự động** nếu policy đã công bố chỉ chọn direct declaration.

### 30.3. Test semantics riêng của resolver

Nếu chính sách cố ý lấy annotation từ parent/interface, cần test resolver trả đúng policy, và test integration rằng **advice thực sự dùng policy đã resolve**.

### 30.4. Test class annotation vs method annotation

Không để một class-level `@Monitored` vô tình làm test method-level `@Audited` luôn pass. Tách mỗi trục với test case riêng.

### 30.5. Test runtime receiver vs static type

Có cùng một target object nhưng gọi qua typed interface và concrete class reference; xác nhận call/execution/interceptor có semantics nhất quán với contract, không phụ thuộc tình cờ vào reference khai báo.

## 31. Lỗi hiểu nhầm phổ biến

**“@Inherited nghĩa annotation được sao chép xuống mọi method của subclass.”** Sai. Quy tắc chỉ áp dụng cho annotations trên class declarations trong superclass chain.

**“Class implements annotated interface nên `Class.getAnnotation` trả annotation ấy.”** Sai theo Java `@Inherited`.

**“Method override tự kế thừa annotation từ method cha.”** Sai.

**“Method kế thừa không override thì annotation đã được copy vào class con.”** Sai. Reflection có thể trả chính `Method` được khai báo ở class cha.

**“Cùng một body thực thi thì nguồn metadata luôn giống nhau.”** Sai. Interface Method, Parent Method và Child Method là các declarations khác nhau.

**“getMethod và getDeclaredMethod tương đương.”** Sai, một bên tìm public inherited methods, bên kia tìm declared methods.

**“Method.getAnnotation luôn tìm annotation của overridden parent methods.”** Sai theo reflection Java mặc định.

**“@annotation, @within và @target chọn cùng một thứ.”** Sai. Subject/method, lexical type và runtime target là những khái niệm khác nhau.

**“Method call và method execution là cùng join point.”** Sai trong các mô hình phân biệt hai loại.

**“Java annotations tự thực thi advice.”** Sai. Cần cơ chế đọc metadata và thực thi interception.

**“Chỉ cần `RetentionPolicy.RUNTIME` là aspect chắc chắn chạy.”** Sai. Retention chỉ giúp metadata có thể được đọc, không đảm bảo weaving/proxy coverage.

**“Meta-annotation tự lan qua annotation type khác như alias.”** Sai, trừ khi resolver/tool có quy tắc riêng.

**“Constructor annotation kế thừa từ constructor cha.”** Sai.

**“Annotation trên parameter chính là annotation trên method.”** Sai.

**“Không có advice log nghĩa method chưa chạy.”** Sai khi bypass, không match hoặc log sink lỗi.

**“Một unit test reflection pass chứng minh pointcut production pass.”** Sai. Cần kiểm actual join points và deployment.

## 32. Bài tập có lời giải

### Bài 1 — Class inheritance

`@Monitored` được khai báo `@Inherited` trên `Parent`. `Child extends Parent` nhưng không có annotation trực tiếp. `Child.class.isAnnotationPresent(Monitored.class)` là gì?

**Đáp án:** `true` theo Java class-level inheritance, nếu annotation retention cho phép reflection và không có điều kiện khác che/đổi kết quả.

### Bài 2 — Interface inheritance

`@Monitored interface Task` và `class TaskImpl implements Task`. `TaskImpl.class.getAnnotation(Monitored.class)` là gì?

**Đáp án:** `null` theo Java default annotation inheritance; implements interface không mang annotation ấy xuống class.

### Bài 3 — Override method

`Parent.process` có `@Audited`, `Child.process` override không annotate. `Child.class.getDeclaredMethod("process").isAnnotationPresent(Audited.class)`?

**Đáp án:** `false`. Method override là declaration mới.

### Bài 4 — Inherited method

`Child` không override `Parent.inherited`. `Child.class.getMethod("inherited").getDeclaringClass()` là gì?

**Đáp án:** `Parent.class` nếu đó là public inherited method được tìm thấy và không có declaration tương ứng ở Child.

### Bài 5 — Reflection dispatch

Một `Method` object đại diện cho `Parent.process` được invoke trên instance `Child` có override. Body nào chạy?

**Đáp án:** Override `Child.process` cho virtual instance method thông thường, dù `Method` object trỏ declaration của Parent. Điều đó không thay annotation được trả từ `Method`.

### Bài 6 — Interface method

Annotation có trên `Task.apply` nhưng không có trên `TaskImpl.apply`. Nếu interceptor kiểm Method từ interface, kết quả có thể khác interceptor kiểm Method từ implementation không?

**Đáp án:** Có. Hai declarations mang metadata khác nhau; cần contract resolver rõ.

### Bài 7 — Retention

`@Audited` không khai báo `@Retention`. Reflection runtime có chắc đọc được annotation đó không?

**Đáp án:** Không. Default retention là `CLASS`, nên runtime reflection annotation thông thường không nhìn thấy nó.

### Bài 8 — `@within`

Method `helper` không annotated, nhưng nằm trong class được annotate. `@within` có giống `@annotation` trên `helper` không?

**Đáp án:** Không. Một tiêu chí dựa trên lexical type, tiêu chí kia dựa trên subject annotation.

### Bài 9 — `@withincode`

Call tới `helper` nằm trong body của `process` được annotate. Có thể match `@withincode(Audited)` dù `helper` không annotated không?

**Đáp án:** Có thể trong AspectJ với join-point kind/cú pháp phù hợp, vì điều kiện xét method **chứa call site**, không phải callee.

### Bài 10 — Policy kế thừa thông qua interface

Ứng dụng muốn mọi implementation của interface có annotated method đều được audit, dù implementation override không annotate. Có dùng `@Inherited` một mình đủ không?

**Đáp án:** Không. Cần chọn theo interface contract hoặc resolver duyệt interface methods, và test thực tế interception boundary.

### Bài 11 — Repeatable annotation

Một type có nhiều annotations cùng kiểu repeatable. Dùng `getAnnotation(A.class)` có đủ lấy toàn bộ không?

**Đáp án:** Không. Dùng `getAnnotationsByType(A.class)` khi cần diễn giải các annotations repeatable được associated với element.

### Bài 12 — Proxy bypass

Annotation lookup trả true nhưng caller gọi raw target ngoài proxy. Advice có chắc chạy không?

**Đáp án:** Không. Metadata và interception coverage là hai bước khác nhau.

## 33. Quy trình thiết kế annotation-based aspect có thể kiểm chứng

1. **Chốt ý nghĩa annotation:** đánh dấu type, method, parameter, field hay type use?
2. **Chốt retention:** engine đọc metadata lúc compile, load hay runtime?
3. **Chốt selection:** chọn join-point kind call hay execution hoặc loại khác?
4. **Chốt declaration precedence:** concrete override, parent method, interface, type-level metadata.
5. **Xác định @Inherited có liên quan không:** chỉ cho class-level annotation theo Java default.
6. **Chốt cách xử lý subclass override:** annotation trực tiếp bắt buộc hay tìm ancestor methods?
7. **Chốt interfaces:** có dò implementation contracts từ interfaces không?
8. **Định nghĩa conflict policy:** nhiều interfaces/annotations khác giá trị thì sao?
9. **Xem xét generics/bridge/default methods** nếu API có chúng.
10. **Xem xét annotation repeatable và type-use** nếu business policy dùng.
11. **Phân biệt static call site với runtime receiver và executed body**.
12. **Kiểm tra weaving/proxy boundaries** trên đúng module/artifact.
13. **Viết positive/negative tests** cho declaration/inheritance/override.
14. **Viết test actual advice fire**, không chỉ reflection.
15. **Viết test nhiều aspect cùng match** để không double count hoặc đổi precedence bất ngờ.
16. **Kiểm tra logger/helper exclusions** nếu pointcut rộng.
17. **Kiểm tra lỗi/cleanup/async** theo contract advice.
18. **Công bố scope và giới hạn** trong tài liệu cho consumer.

## 34. Checklist ghi nhớ

- [ ] Annotation là metadata, tự nó không chạy.
- [ ] `@Target` và `@Retention` giải quyết hai câu hỏi khác nhau.
- [ ] Default retention là CLASS nếu không khai báo.
- [ ] `@Inherited` tác động lên class-level annotation trong superclass chain.
- [ ] Interface annotation không tự lan sang implementation class qua `@Inherited`.
- [ ] Method annotations không tự kế thừa qua override.
- [ ] Method được thừa kế không override có thể vẫn là declaration ở superclass.
- [ ] `Method.getDeclaringClass` cho biết nơi method khai báo.
- [ ] `getMethod` khác `getDeclaredMethod`.
- [ ] `getAnnotation` khác `getDeclaredAnnotation`.
- [ ] Interface Method và implementation Method không đồng nhất metadata.
- [ ] Dynamic dispatch không sao chép annotation giữa declarations.
- [ ] Call join point khác execution join point khi công cụ hỗ trợ.
- [ ] `@annotation` khác `@within`, `@target` và `@withincode`.
- [ ] Một type-level annotation không làm mọi method tự có method annotation.
- [ ] Annotation repeatable có quy tắc container và lookup riêng.
- [ ] Annotation trên parameter/type use phải đọc theo API/designator phù hợp.
- [ ] Generic bridge/synthetic methods cần được kiểm tra khi resolver dùng reflection.
- [ ] Cấu hình inheritance/alias riêng của một công cụ không phải Java default.
- [ ] Annotated element phù hợp không bảo đảm class được weave.
- [ ] Có test direct target/proxy/self-invocation nếu hệ thống dùng proxy.
- [ ] Có test cả metadata matching lẫn actual advice behavior.
- [ ] Không dùng annotation-based matcher đơn lẻ làm security boundary khi còn đường bypass.
- [ ] Mọi thay đổi resolver/pointcut được đánh giá backward compatibility.

## 35. Tài liệu đối chiếu

Những nguồn sau được dùng để tách **quy tắc Java** khỏi **quy tắc của một AOP pointcut language cụ thể**:

- [Java 17 JLS — Annotations](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html): `@Inherited`, `@Retention`, `@Target`, `@Repeatable` và vị trí annotation declaration.
- [Java 17 — `Inherited`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/annotation/Inherited.html): phạm vi của class-level annotation inheritance.
- [Java 17 — `AnnotatedElement`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AnnotatedElement.html): direct/indirect/present/associated annotations và những phương thức lookup.
- [Java 17 — `Class`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): `getMethod`, `getDeclaredMethod` và class annotation reflection.
- [Java 17 — `Method`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html): declaring class, method reflection và invocation.
- [Eclipse AspectJ — Annotations and Pointcuts](https://eclipse.dev/aspectj/doc/latest/adk15notebook/annotations.html): annotation inheritance, `@annotation`, `@within`, `@target`, `@withincode`, parameter annotations và pointcut matching.
- [Eclipse AspectJ — Programming Guide](https://eclipse.dev/aspectj/doc/latest/progguide/progguide.html): khác biệt giữa call và execution join points, signature patterns, pointcuts và advice.

Khi triển khai thật, nên đối chiếu **JDK, AOP compiler/weaver, class loader và deployment** đang dùng; không suy semantics của một công cụ sang tất cả.

## 36. Kết luận

**Kế thừa annotation** và **kế thừa/override method** là những cơ chế khác nhau trong Java. `@Inherited` chỉ giải quyết annotation trên class qua superclass hierarchy; nó không sao chép annotations trên overridden methods hoặc từ interface sang class implementation.

Đối với AOP, cần thêm một phân biệt quan trọng: **metadata của declaration được quan sát không nhất thiết thuộc body thực thi cuối cùng**. Call-site static type, reflective `Method`, runtime receiver và method execution subject có thể chỉ những vị trí khác nhau trong type system.

Do đó, thiết kế annotation-based aspect đúng không chỉ hỏi “đặt `@Audited` ở đâu?”, mà phải nêu rõ **pointcut chọn join-point kind nào, đọc metadata từ declaration nào, có quy tắc kế thừa tùy chỉnh nào, và runtime có thực sự đi qua interception boundary hay không**.

Viết test cho **class-level inheritance**, **override không kế thừa**, **inherited method**, **interface/implementation declarations**, **call/execution khác nhau** và **actual advice fire** là cách biến một quy tắc annotation tưởng như đơn giản thành hợp đồng AOP đáng tin cậy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="annotation-metadata-006">Đọc giá trị cấu hình từ annotation trong advice</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc giá trị cấu hình từ annotation trong advice — Aspect/AOP

Trong lập trình hướng khía cạnh (**Aspect-Oriented Programming — AOP**), một **advice** có thể cần thông tin cấu hình riêng cho từng điểm can thiệp: tên nhóm nghiệp vụ để gắn vào log, mức độ theo dõi, có được ghi kết quả hay không, ngưỡng thời gian, chiến lược chọn những dữ kiện an toàn, hoặc mã chính sách kiểm toán. Nếu nhúng hết những thông tin này trong thân advice bằng các phép so sánh tên method, mã sẽ nhanh chóng khó bảo trì.

**Annotation** cho phép gắn **siêu dữ liệu có kiểu** vào một declaration. Ví dụ, một phương thức có thể mang một annotation như `@Observe(category = "inventory", logReturn = false)`. Khi một join point tương ứng được can thiệp, advice có thể **nhận annotation đã được bind bởi công cụ AOP**, hoặc **đọc annotation qua Java reflection**, rồi dùng các giá trị của nó theo một hợp đồng đã định nghĩa.

Điểm quan trọng là phải tách ba câu hỏi:

1. **Điểm nào được chọn?** Một pointcut có thể chọn những method có annotation hoặc những method execution thuộc tập mục tiêu.
2. **Giá trị annotation nào được đọc?** Annotation ở method khai báo trên interface hay method implementation, trên class, parameter, hay annotation được kế thừa? Quy tắc tìm kiếm phải rõ.
3. **Advice được phép làm gì với giá trị ấy?** Ví dụ, `logReturn=true` không được trở thành quyền ghi token bí mật, và `maxAttempts=3` không tự có nghĩa retry mọi giao dịch an toàn.

Vì vậy, **đọc cấu hình từ annotation trong advice** là cầu nối giữa **metadata của chương trình** và **hành vi cắt ngang có kiểm soát**. Bài này trình bày Aspect/AOP cơ bản và nâng cao **nói chung**. Khi đưa ra cú pháp pointcut đặc thù của một ngôn ngữ AOP, bài sẽ ghi nhãn riêng. Ví dụ đầy đủ sử dụng **Java 17 và JDK dynamic proxy** để mô phỏng cơ chế gọi advice, không nhận nó là một weaver bao phủ mọi join point.

## 1. Mục tiêu học tập

Sau bài, bạn có thể:

1. Tạo annotation chứa các **annotation elements** có kiểu, giá trị mặc định và giới hạn vị trí áp dụng.
2. Phân biệt `@Retention(SOURCE)`, `CLASS`, `RUNTIME`; biết vì sao reflection runtime thường cần `RUNTIME`.
3. Phân biệt `@Target(METHOD)`, `TYPE`, `PARAMETER` và các nơi khác theo Java.
4. Đọc cấu hình qua `getAnnotation`, `getDeclaredAnnotation`, `getAnnotationsByType` hoặc thông qua annotation binding trong pointcut nếu công cụ hỗ trợ.
5. Xử lý trường hợp method khai báo trên **interface** khác method cụ thể trên **class triển khai**, đặc biệt với proxy.
6. Quy định precedence giữa annotation ở implementation/interface/type và fallback, tránh lấy nhầm giá trị.
7. Kiểm tra mặc định, giá trị không hợp lệ, annotation lặp, meta-annotation, kế thừa và reflection trên phương thức generic/bridge.
8. Đọc cấu hình một cách **an toàn**: không lộ dữ liệu nhạy cảm, không để flag tùy tiện vô hiệu chính sách bắt buộc.
9. Nhận diện giới hạn của **compile-time/weaving**, proxy, module, class loader, self-invocation và caching.
10. Viết, chạy và phân tích một chương trình Java 17 minh họa đọc annotation trong advice, bảo toàn kết quả và lỗi.
11. Thiết kế kiểm thử cho điểm được annotate, không được annotate, thiếu runtime retention, override/fallback, lỗi và concurrency.

## 2. Annotation là gì, và không phải là gì?

Trong Java, một annotation type được khai báo bằng `@interface` và cung cấp **các phần tử dữ liệu có kiểu**:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface OperationTag {
    String category() default "general";
    boolean logReturn() default false;
    int slowThresholdMs() default 100;
    String[] tags() default {};
}
```

Đây là một **đoạn khai báo hợp lệ** nhưng chưa phải một chương trình độc lập có `main`. Các biểu thức như `category()` và `logReturn()` là **annotation elements**; khi đọc một instance annotation, Java cung cấp các phương thức này để lấy giá trị. Chúng không phải những instance fields mutable được sửa tùy ý sau khi đọc.

Dùng trên một method:

```java
@OperationTag(
    category = "inventory",
    logReturn = false,
    slowThresholdMs = 250,
    tags = {"read", "internal"}
)
public String load(String sku) {
    return "item:" + sku;
}
```

Đây là **snippet minh họa method**, không tự biên dịch như một file độc lập nếu thiếu class chứa nó.

Điều annotation **không tự thực hiện**:

- Không tự chạy advice chỉ vì nó tồn tại.
- Không tự tạo pointcut, weaving hoặc proxy.
- Không tự bảo đảm authorization, logging, transaction hay retry.
- Không tự thay đổi mỗi khi file cấu hình ngoài ứng dụng đổi: giá trị annotation thường là **metadata đi cùng mã đã biên dịch**.
- Không tự “lan” từ interface method sang implementation method theo quy tắc mà người viết mong muốn.
- Không tự thẩm định giá trị an toàn; `slowThresholdMs = -10` vẫn cần được phát hiện nếu contract không cho phép số âm.

**Annotation là một nguồn metadata; logic AOP quyết định cách chọn, đọc, kiểm tra và sử dụng nguồn ấy.**

## 3. Những phần tử và giá trị được phép trong annotation

Annotation elements trong Java được phép có những kiểu phù hợp như các primitive, `String`, `Class`, enum, annotation type khác và mảng một chiều của những kiểu được phép. Không khai báo tùy ý một `List<String>` làm annotation element rồi mặc định compiler chấp nhận.

Ví dụ:

```java
enum Sensitivity { PUBLIC, INTERNAL, RESTRICTED }

@interface RecordPolicy {
    String category();
    Sensitivity sensitivity() default Sensitivity.INTERNAL;
    Class<?> serializer() default Object.class;
    String[] labels() default {};
}
```

Đây là các khai báo minh họa, không có `main`. Nếu một element **không có default**, nơi dùng annotation phải cung cấp nó theo luật Java. Nếu có default, advice đọc giá trị đã được xác định theo annotation contract mà không cần tự điền bằng một câu lệnh if cho từng nơi sử dụng.

### 3.1. Mặc định không có nghĩa “không tồn tại”

Với:

```java
@interface TraceRule {
    String group() default "general";
    boolean revealResult() default false;
}
```

Khai báo `@TraceRule` và `@TraceRule(group = "general", revealResult = false)` có thể cho advice **cùng các giá trị phần tử**. Một advice dùng `rule.group()` sẽ thấy `"general"` trong cả hai trường hợp.

Nếu thiết kế bắt buộc phân biệt **“caller không đặt”** với **“caller cố ý đặt bằng default”**, annotation element mặc định không tự mang cờ đó. Cần thiết kế contract khác, chẳng hạn sentinel có ý nghĩa, tách annotation hoặc dùng cấu hình tường minh; không suy nguồn gốc giá trị từ dữ liệu runtime thông thường.

### 3.2. Giá trị cấu hình phải được kiểm tra

Dù compiler kiểm tra kiểu, nó không tự biết `slowThresholdMs=-1` có vô nghĩa với nghiệp vụ không. Hãy đặt ràng buộc:

- `category` không được rỗng nếu là khóa metric.
- `slowThresholdMs` không âm và không vượt ngân sách hợp lý.
- `logReturn` chỉ áp với những kết quả đã được phân loại an toàn.
- `tags` nên giới hạn số lượng/độ dài và tránh cardinality bùng nổ.
- `Class<?>` chỉ được dùng làm strategy type sau khi kiểm tra tương thích và quyền khởi tạo.

Không dùng annotation làm kênh nạp code chưa tin cậy chỉ vì nó đã được biên dịch.

## 4. `@Retention` quyết định khả năng đọc runtime

Với **advice đọc cấu hình qua Java Reflection tại runtime**, annotation type thường phải khai báo `@Retention(RetentionPolicy.RUNTIME)`. Nếu bỏ `@Retention`, giá trị mặc định là `CLASS`; `SOURCE` cũng không được reflection cung cấp. Điều này không phủ nhận khả năng một compiler/binary weaver đọc `SOURCE` hoặc `CLASS` metadata ở **giai đoạn khác**.

Ví dụ quan sát cần một annotation instance để đọc các element cấu hình:

```java
Observe mark = method.getAnnotation(Observe.class);
```

Giá trị `mark` vẫn có thể là `null` dù annotation đã xuất hiện trong source nếu resolver đọc **sai declaration**. Cần xác định rõ method thuộc interface hay implementation. `@Target(METHOD)` chỉ cho phép gắn trên method declaration; `@Target(TYPE)`, `PARAMETER` và `TYPE_USE` diễn đạt vị trí khác, cần API đọc tương ứng (`Class.getAnnotation`, `getParameterAnnotations`, `AnnotatedType`).

**Điểm riêng của bài đọc cấu hình:** trước khi gọi các element như `mark.operation()`, phải biết annotation được resolve theo chính sách precedence nào; nếu không tìm thấy, chọn rõ default/failure policy thay vì âm thầm áp một rule tùy ý. Nếu advice bắt buộc đối với an ninh hoặc audit, không coi `null` là lý do tự động bỏ qua kiểm soát. `@Retention` và `@Target` nên được khai báo tường minh để caller biết nơi nào có thể cung cấp cấu hình hợp lệ.

## 5. Hai cách đưa annotation vào advice

### 5.1. Binding ngay trong pointcut khi công nghệ hỗ trợ

Một số ngôn ngữ AOP cho phép pointcut kiểm tra một annotation hiện diện tại join point và **bind** annotation instance cho tham số của advice. Pseudocode:

```text
pointcut: methodExecutionHasAnnotation(Observe config)

before(config):
    validate(config)
    record(config.category())
```

Đây là **giả mã có chủ đích**, không phải API chung của Java hoặc mọi AOP. Trong một công cụ có khả năng annotation pointcut và binding, cách này có thể tránh việc advice tự đoán method nào mang annotation. Tuy nhiên vẫn phải hiểu **annotation được xét ở declaration nào**, khả năng binding class/method/parameter và các giới hạn của weaving.

Một ví dụ **cú pháp riêng cho AspectJ**, cần các declarations/type phù hợp và AOP compiler/weaver, không chạy bằng `javac` thông thường:

```java
// Observe là annotation type có @Retention(RUNTIME) và @Target(METHOD).
public aspect ObservationAspect {
    before(Observe cfg):
        execution(* *(..)) && @annotation(cfg) {
        System.out.println("Category = " + cfg.category());
    }
}
```

Đây là minh họa ngữ nghĩa **binding annotation ở join point execution**. Không suy rằng một annotation chỉ đặt trên interface method chắc chắn được binding khi weaver đang chọn **implementation execution**. Quy tắc declaration của pointcut, dispatch và weaving thực tế quyết định kết quả.

### 5.2. Đọc qua reflection trong advice/interceptor

Khi cơ chế AOP cung cấp `Method` hoặc dữ liệu join point, advice có thể gọi reflection:

```java
Observe config = method.getAnnotation(Observe.class);
if (config != null) {
    String category = config.category();
    boolean logReturn = config.logReturn();
}
```

Đây là **snippet minh họa**, giả định có biến `method` và annotation `Observe`. Ưu điểm là dùng các API Java quen thuộc, nhưng phải xác định:

- `method` có phải method trên interface proxy hay method concrete class?
- Metadata có retention RUNTIME không?
- Implementation override có khai báo annotation khác không?
- Annotation được đặt ở class, method hay parameter?
- Method là synthetic/bridge method hay là method được khai báo trong superclass?
- Access tới class/method có bị module/classloader giới hạn không?
- Cơ chế AOP có thực sự đưa lời gọi đi qua advice này không?

**Đọc một `Method` đúng tên không luôn đủ.** Một method tương ứng ở interface và một method ở implementation là **hai declaration khác nhau**, nên có thể mang metadata khác.

## 6. Lấy đúng method: interface và implementation

Với JDK dynamic proxy, `InvocationHandler.invoke(proxy, method, args)` thường nhận một `Method` thuộc **giao diện mà proxy cung cấp**. Nếu chỉ gọi `method.getAnnotation`, một annotation gắn **chỉ trên implementation** có thể không thấy.

Ví dụ:

```java
interface Store {
    void save(String id);
}

class StoreImpl implements Store {
    @Observe(category = "database-write")
    public void save(String id) {}
}
```

Đoạn này minh họa một cặp declaration; cần định nghĩa `Observe`. Trong `InvocationHandler`, `method` tương ứng `Store.save` có thể không mang annotation, trong khi `StoreImpl.save` mang nó.

Một chiến lược đơn giản cho **public method có cùng parameter types**:

```java
Method implementationMethod =
    target.getClass().getMethod(
        interfaceMethod.getName(),
        interfaceMethod.getParameterTypes());

Observe config = implementationMethod.getAnnotation(Observe.class);
if (config == null) {
    config = interfaceMethod.getAnnotation(Observe.class);
}
```

Đây là snippet, giả định có `target`, `interfaceMethod`, kiểu `Observe` và các import reflection. Nó thể hiện **quy tắc ưu tiên do ứng dụng thiết kế**: annotation ở implementation được ưu tiên, nếu vắng thì lấy annotation từ interface. Đây **không phải quy tắc mặc định bắt buộc** của mọi AOP.

### 6.1. Vì sao không chỉ dùng tên method?

Nếu target có overload:

```java
void process(String id);
void process(String id, boolean dryRun);
```

thì `getMethod("process", parameterTypes)` cần danh sách parameter types chính xác. Dùng chỉ `getName()` hoặc tìm method đầu tiên bằng stream có thể đọc nhầm annotation của overload khác.

Các vấn đề nâng cao còn có **varargs**, generic erasure, covariant return, bridge/synthetic methods và method được thừa hưởng. Java reflection trả `Method` của declaration có thể khác nơi cấu hình thực sự được định nghĩa; cần hợp đồng lookup cụ thể.

### 6.2. Interface annotation không tự kế thừa sang implementation method

Trong Java thông thường, annotation trên method khai báo của interface **không tự xuất hiện** khi hỏi:

```java
implementationMethod.getAnnotation(Observe.class)
```

Nếu implementation không tự khai báo annotation ấy, kết quả có thể là `null`. Vì vậy nếu muốn interface đóng vai trò fallback, cần **thực hiện lookup rõ ràng** hoặc dùng khả năng metadata resolution của chính công cụ AOP nếu có.

### 6.3. Quy tắc khi có nhiều interface

Một class có thể triển khai hai interfaces đều chứa method cùng chữ ký nhưng annotation khác nhau. Một JDK proxy có thể nhận `Method` tương ứng một interface theo quy tắc duplicate methods của proxy; nó không tự biết caller đã cast proxy sang interface nào.

Nếu hai annotations trái nghĩa, không nên chọn tùy tiện “interface xuất hiện đầu tiên”. Phải quyết định **một contract thống nhất cho method**, hoặc phát hiện conflict và từ chối cấu hình; với chính sách bảo mật bắt buộc, không được suy ra ALLOW từ một annotation vô tình thuận tiện hơn.

## 7. Thứ tự ưu tiên metadata phải thành hợp đồng

Có nhiều vị trí khả dĩ:

1. Annotation gắn trực tiếp vào **implementation method**.
2. Annotation gắn trực tiếp vào **interface method**.
3. Annotation trên **class implementation**.
4. Annotation trên **interface type**.
5. Annotation từ **superclass hoặc overridden method**.
6. Annotation ở **package/module** hoặc nguồn cấu hình ngoài.
7. Giá trị mặc định của annotation hoặc mặc định của policy.

Không có một hệ thống tự động duy nhất cho tất cả. **Hãy công bố thứ tự ưu tiên** và test từng trường hợp.

Ví dụ một quy ước cho advice quan sát:

```text
implementation method annotation
  -> matching interface method annotation
  -> implementation class default annotation
  -> local observer default
```

Nếu cho phép “implementation override interface”, cần xem chính sách ấy có hợp lý trong tổ chức: nếu interface annotation mang một **yêu cầu bắt buộc**, cho phép implementation tắt bằng `enabled=false` có thể phá bất biến. Quy tắc cho **telemetry tùy chọn** có thể khác hoàn toàn quy tắc cho **audit/authorization bắt buộc**.

### 7.1. Tách “không có annotation” và “cấu hình tắt”

Hai trường hợp:

- Method **không gắn annotation**.
- Method có `@Observe(enabled = false)`.

Chúng không nhất thiết giống nhau. Trường hợp đầu có thể là **không thuộc pointcut**; trường hợp hai có thể là **thuộc phạm vi chính sách nhưng chủ động từ chối observation**. Với bảo mật/audit, việc tắt phải được policy owner chấp thuận; không để một annotation do module không tin cậy tự tuyên bố không cần guard.

### 7.2. Tính nhất quán giữa pointcut và lookup

Một lỗi khó phát hiện: pointcut chỉ khớp annotation **trên interface** nhưng advice lại chỉ đọc **implementation method**. Lúc đó có thể:

- Pointcut được chọn nhưng advice thấy `null`.
- Advice mặc định sai và bỏ qua cấu hình.
- Hoặc điểm thực tế không được chọn vì công cụ xét một declaration khác.

Định nghĩa rõ **quy tắc nào chọn join point** và **quy tắc nào lấy annotation instance**, rồi kiểm tra hai bước khớp nhau.

## 8. Ví dụ Java 17 runnable: advice đọc cấu hình runtime

Chương trình sau minh họa **trong cùng một ví dụ**:

- Một annotation `@Observe` có `RUNTIME` retention, chỉ gắn lên method, chứa `category`, `logReturn` và `tags`.
- Một method `fetch` được annotate **chỉ ở interface**; advice đọc được nhờ fallback.
- Một method `save` có annotation ở cả interface và implementation; **implementation được ưu tiên** theo quy tắc tự thiết kế.
- Một method `reject` có annotation **chỉ ở implementation**, target ném lỗi; advice báo lỗi rồi ném lại nguyên gốc.
- Một method `plain` không gắn annotation nên handler chỉ gọi target, không phát thông báo advice.
- Kết quả an toàn `saved:B2` mới được in khi `logReturn=true`; không in kết quả của `fetch` trong advice.
- Lời gọi qua **JDK dynamic proxy** được can thiệp; ví dụ không weave trực tiếp class target.

**Lưu ý:** chương trình dùng `System.out` để có stdout xác định. Việc log kết quả dựa vào một dữ liệu mô phỏng không nhạy cảm; trong hệ thật phải kiểm tra classification/redaction/retention trước khi cho phép in result. Đây không phải ví dụ bảo mật hoặc kiểm toán bền vững.

Tạo file `AnnotationConfigAdviceDemo.java`:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;

public class AnnotationConfigAdviceDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Observe {
        String category() default "general";
        boolean logReturn() default false;
        String[] tags() default {};
    }

    public interface Gateway {
        @Observe(category = "read", tags = {"public", "safe"})
        String fetch(String id);

        @Observe(category = "interface-write")
        String save(String id);

        String reject(String id);
        String plain();
    }

    public static final class GatewayImpl implements Gateway {
        @Override
        public String fetch(String id) {
            System.out.println("TARGET fetch " + id);
            return "value:" + id;
        }

        @Override
        @Observe(category = "impl-write",
                 logReturn = true, tags = {"mutation"})
        public String save(String id) {
            System.out.println("TARGET save " + id);
            return "saved:" + id;
        }

        @Override
        @Observe(category = "reject")
        public String reject(String id) {
            System.out.println("TARGET reject " + id);
            throw new IllegalArgumentException("blocked:" + id);
        }

        @Override
        public String plain() {
            System.out.println("TARGET plain");
            return "no-advice";
        }
    }

    static Observe configuration(
            Method interfaceMethod, Class<?> targetClass) {
        final Method implementation;
        try {
            implementation = targetClass.getMethod(
                    interfaceMethod.getName(),
                    interfaceMethod.getParameterTypes());
        } catch (NoSuchMethodException error) {
            throw new IllegalStateException(
                    "implementation method not found", error);
        }

        Observe local = implementation.getAnnotation(Observe.class);
        return local != null
                ? local
                : interfaceMethod.getAnnotation(Observe.class);
    }

    static Object invokeTarget(
            Method method, Object target, Object[] args)
            throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException wrapped) {
            throw wrapped.getCause();
        }
    }

    static Gateway wrap(Gateway target) {
        return (Gateway) Proxy.newProxyInstance(
                Gateway.class.getClassLoader(),
                new Class<?>[] { Gateway.class },
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return invokeTarget(method, target, args);
                    }

                    Observe cfg = configuration(method, target.getClass());
                    if (cfg == null) {
                        return invokeTarget(method, target, args);
                    }

                    String category = cfg.category();
                    if (category.isBlank()) {
                        throw new IllegalStateException("blank category");
                    }

                    System.out.println("ADVICE before " + category
                            + " tags=" + Arrays.toString(cfg.tags()));

                    try {
                        Object result = invokeTarget(method, target, args);
                        if (cfg.logReturn()) {
                            System.out.println("ADVICE return " + result);
                        }
                        return result;
                    } catch (Throwable error) {
                        System.out.println("ADVICE throw "
                                + error.getClass().getSimpleName());
                        throw error;
                    } finally {
                        System.out.println("ADVICE finally " + category);
                    }
                });
    }

    public static void main(String[] args) {
        Gateway port = wrap(new GatewayImpl());

        System.out.println("CALL " + port.fetch("A1"));
        System.out.println("CALL " + port.save("B2"));

        try {
            port.reject("C3");
        } catch (IllegalArgumentException error) {
            System.out.println("CALL_ERROR " + error.getMessage());
        }

        System.out.println("CALL " + port.plain());
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 AnnotationConfigAdviceDemo.java
java AnnotationConfigAdviceDemo
```

**Stdout dự kiến chính xác**:

```text
ADVICE before read tags=[public, safe]
TARGET fetch A1
ADVICE finally read
CALL value:A1
ADVICE before impl-write tags=[mutation]
TARGET save B2
ADVICE return saved:B2
ADVICE finally impl-write
CALL saved:B2
ADVICE before reject tags=[]
TARGET reject C3
ADVICE throw IllegalArgumentException
ADVICE finally reject
CALL_ERROR blocked:C3
TARGET plain
CALL no-advice
```

### 8.1. Vì sao `fetch` đọc được annotation?

`Gateway.fetch` có `@Observe(category="read", ...)`; implementation `GatewayImpl.fetch` không có annotation. Trong handler, `configuration` tìm method của target, thử lấy annotation ở implementation (nhận null), sau đó **fallback về method interface** do proxy cung cấp.

Vì vậy `ADVICE before read ...` xuất hiện. `logReturn` lấy default `false`, nên không có dòng `ADVICE return value:A1`, dù caller vẫn nhận `value:A1`.

### 8.2. Vì sao `save` dùng `impl-write` thay `interface-write`?

Cả interface và implementation đều gắn `@Observe`. Hàm `configuration` đặt **ưu tiên implementation**; khi annotation địa phương khác null, nó được trả ngay.

Do đó nhãn là `impl-write` và `logReturn=true`, khiến advice in kết quả `saved:B2`. Đây là **quy tắc do ví dụ thiết kế**, không phải một quy tắc được mọi framework AOP áp dụng.

### 8.3. Vì sao `reject` vẫn chạy advice và trả lỗi gốc?

`Gateway.reject` không annotate, nhưng `GatewayImpl.reject` có `@Observe(category="reject")`. Hàm lookup tìm được annotation trên implementation nên advice được áp.

Target in `TARGET reject C3` rồi ném `IllegalArgumentException("blocked:C3")`. `Method.invoke` bọc ngoại lệ target bằng `InvocationTargetException`; `invokeTarget` lấy `getCause()`, advice ghi loại lỗi rồi **ném lại chính lỗi đó**. Caller bắt đúng `IllegalArgumentException`. `finally` vẫn chạy trên đường throw.

### 8.4. Vì sao `plain` không có dòng ADVICE?

Method này không có annotation ở interface lẫn implementation. `configuration` trả null, handler chuyển thẳng tới target. Do vậy `TARGET plain` xuất hiện trước `CALL no-advice`, không có advice messages.

### 8.5. Những giới hạn phải nhận ra

- Handler dùng JDK dynamic proxy; lời gọi **trực tiếp** `new GatewayImpl().save(...)` không qua proxy nên không chạy advice.
- Handler minh họa method public trên một interface. Nó không tự chặn field access, constructor, private method hoặc self-invocation qua target.
- Việc lookup interface bằng `Method` proxy chỉ phục vụ trường hợp đơn giản, không giải quyết xung đột nhiều interfaces cùng method signature.
- `targetClass.getMethod` tìm public method; nếu code thật có bridge methods, generic signatures, interface defaults hoặc nhiều overload, cần resolver và tests chính xác hơn.
- `configuration` phản chiếu mỗi invocation cho dễ hiểu; ứng dụng tải lớn có thể cache metadata an toàn theo method và class với cấu trúc concurrency/loader lifecycle phù hợp.
- Ví dụ không có async, transaction, retry, audit bền vững hoặc logger có thể lỗi. Những chính sách ấy không xuất hiện tự động từ `@Observe`.
- Annotation `logReturn=true` **không phải quyền log mọi payload**. Dữ liệu nhạy cảm và chính sách privacy phải được kiểm tra độc lập.
- `category.isBlank()` chỉ kiểm tra một ràng buộc đơn giản; production nên kiểm tra cả độ dài, allowed values và metadata khác.
- Handler không hỗ trợ nhiều chính sách cùng lúc, pointcut precedence hoặc selector khác annotation; các mối quan hệ đó thuộc thiết kế rộng hơn.

## 9. `getAnnotation`, `getDeclaredAnnotation`, `getAnnotationsByType`

Các API đọc metadata không hoàn toàn cùng ý nghĩa.

| API | Ý nghĩa cần lưu ý |
| --- | --- |
| `element.getAnnotation(A.class)` | Lấy annotation A theo quy tắc Java của phần tử được phản chiếu, hoặc null |
| `element.getDeclaredAnnotation(A.class)` | Xét annotation **trực tiếp** trên declaration theo quy tắc API |
| `element.getAnnotations()` | Liệt kê annotations hiện diện theo semantics của element |
| `element.getDeclaredAnnotations()` | Chỉ annotations khai báo trực tiếp |
| `element.getAnnotationsByType(A.class)` | Hữu ích cho **repeatable annotations**, xét container và một số quy tắc inheritance |
| `method.getParameterAnnotations()` | Annotation trên từng parameter declaration |
| `method.getAnnotatedReturnType()` | Type-use annotations trên kiểu trả về, nếu có |

### 9.1. `@Inherited` không làm method annotation tự thừa kế

`@Inherited` trong Java áp cho annotation type dùng trên **classes** theo semantics kế thừa annotation của class/subclass. Nó **không khiến annotation gắn trên interface method hoặc overridden method tự xuất hiện** khi đọc implementation method.

Ví dụ một class con kế thừa implementation của một method không đồng nghĩa annotation của một method override được “chuyển” theo cách tùy ý. Chỉ dựa trên `@Inherited` mà bỏ lookup interface/supermethod sẽ tạo lỗi cấu hình.

Cũng không mặc định annotation trên interface type được coi là annotation inherited trên implementing class: phải xem quy tắc `AnnotatedElement` và chủ động thiết kế fallback nếu cần.

### 9.2. Repeatable annotations

Một annotation đánh dấu `@Repeatable(Container.class)` có thể xuất hiện nhiều lần ở cùng nơi nếu khai báo container hợp lệ. Khi muốn đọc **tất cả bản cấu hình**, `getAnnotationsByType(A.class)` là API phù hợp hơn so với `getAnnotation(A.class)` đơn lẻ.

Ví dụ về metadata mong muốn:

```java
@Tag("finance")
@Tag("critical")
void process() {}
```

Đây là snippet **chỉ minh họa cách sử dụng**, chưa định nghĩa `@Tag`, container hoặc class, nên không chạy độc lập. Phải xác định **thứ tự và quy tắc hợp nhất** nhiều cấu hình: có cộng dồn hay annotation thứ hai đè thứ nhất? Nếu hai `@RetryPolicy` cùng có max attempts khác nhau, không nên chọn một cách ngẫu nhiên.

### 9.3. Meta-annotations không được quét đệ quy mặc định

Một annotation `@CriticalOperation` có thể tự mang `@Observe(category="critical")` nếu annotation target cho phép áp vào annotation type. Nhưng:

```java
method.getAnnotation(Observe.class)
```

không tự đảm bảo sẽ **truy vết annotation nằm trên annotation type khác** để tìm Observe trong mọi trường hợp. Cần code tìm kiếm meta-annotation có quy tắc rõ hoặc sử dụng khả năng của một thư viện/công cụ nếu đã được xác minh.

Các câu hỏi cần định nghĩa: quét mấy tầng, xử lý vòng meta-annotations ra sao, giá trị annotation nào được ưu tiên, và có hỗ trợ alias thuộc tính không? Không có một cơ chế alias/merge chung do Java reflection tự tạo cho mọi annotation.

## 10. Cấu hình kiểu hợp đồng hay chỉ là gợi ý?

Không phải mọi giá trị metadata có cùng mức tin cậy.

**Gợi ý quan sát:** `category="inventory"`, `samplePercent=20`, `logReturn=false`. Chúng có thể quyết định mức quan sát **nếu** không bỏ qua các nghĩa vụ bắt buộc.

**Ràng buộc bắt buộc:** `requiresAuthorization=true`, `auditRequired=true` hoặc `maxRetries` với một operation nhạy cảm. Nếu annotation được đặt/sửa bởi thành phần không nắm quyền chính sách, không được mặc định nó có thể tắt một guard có tính bắt buộc.

### 10.1. Không đặt quyền truy cập hoàn toàn trong một Boolean annotation

Nếu một method tự khai báo `@ProtectedOperation(enabled=false)`, điều đó không chứng minh thao tác được phép chạy mà không kiểm tra quyền. Cần xác định **nguồn tin cậy của chính sách**, ranh giới thực thi bắt buộc và cách xử lý khi annotation thiếu hoặc sai.

Với guard, “annotation không có” có thể phải **fail closed** chứ không phải “bỏ qua aspect” nếu ranh giới đó cần luôn được kiểm soát.

### 10.2. Cấu hình log cũng có thể gây rò dữ liệu

`@Observe(logReturn=true)` nghe tiện, nhưng một method có thể trả thông tin sức khỏe, credentials, tài khoản hoặc object chứa field nhạy cảm. Gọi `toString()` bừa bãi có thể lộ dữ liệu hoặc ném lỗi.

Nên thiết kế:

- Allowlist của những loại result được phép ghi.
- Mask/redact theo schema và quyền của caller/sink.
- Giới hạn độ dài, tần suất, cardinality, retention.
- Không ghi trực tiếp `Throwable.getMessage()` nếu chứa payload nhạy cảm.
- Không in toàn bộ `args` chỉ vì annotation bật một flag.
- Quy tắc logger lỗi để không làm sai return/throw của target khi chỉ là observer tùy chọn.

Trong ví dụ Java, chuỗi `saved:B2` là dữ liệu học tập được giả định an toàn; không phải lời khuyên log kết quả bất kỳ.

## 11. Validation: thời điểm nào và khi sai phải làm gì?

Có ba chiến lược phổ biến:

1. **Compile-time/build validation:** công cụ kiểm tra annotation đúng nơi và giá trị thỏa ràng buộc có thể biết sớm.
2. **Startup validation:** quét tập join point được quản lý, phát hiện cấu hình rỗng/trái ngữ nghĩa trước khi hệ phục vụ.
3. **Per-invocation validation:** kiểm tra ở thời điểm advice chạy; hữu ích khi target/metadata động, nhưng có chi phí runtime và lỗi đến muộn.

Không phải hệ thống nào cũng có khả năng quét toàn bộ classes khi startup, nhất là với dynamic proxies/module/plugin loaders. Nên kết hợp **chứng cứ có thật** thay vì tuyên bố đã bao phủ hết hệ chỉ nhờ một bước scan.

### 11.1. Quy tắc lỗi phải tùy chính sách

Nếu annotation telemetry có `category=""`:

- Có thể từ chối khởi động module nếu cấu hình invalid là lỗi phát triển.
- Hoặc phát tín hiệu degraded và không ghi sample **nếu** telemetry thật sự tùy chọn và hợp đồng cho phép.

Nếu annotation kiểm soát quyền bắt buộc không hợp lệ:

- Không được tự rơi về `ALLOW`.
- Cần từ chối thao tác, báo cấu hình lỗi và sửa theo quy trình đáng tin.

**Không dùng một catch rộng nuốt `Throwable` và tiếp tục target cho mọi annotation**, vì nó có thể che chính sách cần fail closed.

### 11.2. Xử lý default khi tiến hóa phiên bản annotation

Khi thêm element mới vào annotation dùng ở nhiều module, có default hợp lệ có thể hỗ trợ tương thích. Nhưng đổi ý nghĩa default có thể thay hành vi của **tất cả methods đã annotate**.

Nếu element mới **không có default**, những declaration cũ có thể không thỏa contract khi biên dịch mới. Với class files biên dịch từ các phiên bản annotation khác nhau, có thể gặp lỗi metadata/runtime như `IncompleteAnnotationException` khi đọc phần tử còn thiếu trong một số điều kiện.

Cần kiểm tra binary/source compatibility và chạy đúng artifact triển khai, không chỉ unit test với tất cả classes được rebuild đồng thời.

## 12. Annotation và tham số binding của advice

Một advice thường cần **hai nhóm dữ liệu**:

- **Metadata**: annotation instance chứa category, threshold, mode, tags.
- **Invocation data**: method, target, args, kiểu return, exception, thời gian bắt đầu, context của lời gọi.

Đừng nhầm `@Observe(category="A")` với argument `category` do caller truyền khi chạy. Annotation là **metadata declaration**; args thuộc **lượt gọi runtime**.

Pseudocode:

```text
advice around join point:
    config = resolveAnnotation(joinPoint)
    validate(config)
    started = monotonicClock()
    try:
        result = proceed exactly once
        if config permits safe result recording:
            record(result)
        return result
    catch original error:
        recordSafeFailure(error)
        rethrow original error
    finally:
        recordDuration(config, monotonicClock() - started)
```

Đây là **giả mã**; không có API chung tên `proceed` cho mọi công nghệ. Chỉ xét điểm advice thực sự được áp.

### 12.1. Threshold và đồng hồ

Nếu annotation có `slowThresholdMs=250`, đo elapsed bằng nguồn **monotonic** phù hợp như `System.nanoTime()` để so khoảng thời lượng, thay vì lấy chênh lệch wall-clock có thể thay đổi do chỉnh thời gian.

Chú ý overflow/đổi đơn vị, và chỉ kết luận “slow” nếu policy đã định nghĩa đo **method execution đồng bộ** hay **completion của future**. Với method trả `CompletableFuture`, return future không đồng nghĩa công việc đã xong.

### 12.2. Exception contract

Một aspect quan sát không nên dùng giá trị annotation để tùy tiện chuyển lỗi nghiệp vụ thành `null`. Nếu target ném lỗi, advice nên bảo toàn exception theo hợp đồng. Nếu logging backend ném lỗi, xử lý **riêng lỗi logging** theo chính sách của observer, không catch toàn bộ `proceed` rồi tuyên bố target thành công.

### 12.3. Thứ tự nhiều advice

Một method có thể đồng thời mang annotation đo thời gian và annotation yêu cầu kiểm toán. Thứ tự ảnh hưởng:

- Thời lượng đo toàn chuỗi hay chỉ target?
- Audit ghi attempt, return bình thường hay committed effect?
- Retry có gọi target nhiều lần?
- Authorization có chạy trước cache-hit short circuit?
- Lỗi exporter có che lỗi target không?

Annotation mô tả cấu hình; nó **không tự quyết định precedence**. Cần quy tắc phối hợp độc lập theo cơ chế AOP và test.

## 13. Tối ưu reflection và cache cấu hình

Mỗi lần invocation gọi `target.getClass().getMethod(...).getAnnotation(...)` có thể có overhead. Khi metadata của class ổn định, có thể cache một **resolved configuration** theo khóa đủ chính xác, ví dụ:

```text
(target runtime class, interface Method, annotation type)
    -> validated immutable configuration
```

Đây là mô hình khóa cache; tùy thiết kế có thể cần cả class loader/biến thể generic/pointcut. Một `ConcurrentHashMap` có thể giúp thread safety, nhưng nó **có thể giữ mạnh key `Class`** và làm class loader khó được thu gom trong môi trường plugin/redeploy.

Cần chọn phương án phù hợp:

- Cache metadata theo `ClassValue` khi phù hợp để tránh một số kiểu retention.
- Không cache bằng key chỉ là **tên method**, vì overload sẽ xung đột.
- Có chiến lược cho **dynamic class loading/redefinition** nếu môi trường hỗ trợ.
- Validation được làm trước khi đưa vào cache.
- Phân biệt “không có annotation” với “annotation bị lỗi”.
- Nếu annotation type thay đổi khi deploy, cache cũ không tự phản ánh artifact mới.
- Dùng microbenchmark đáng tin để chứng minh reflection là bottleneck trước khi làm cache phức tạp.

**Không phải lúc nào cache cũng tốt hơn.** Với lưu lượng thấp, lookup đơn giản dễ hiểu và ít rủi ro hơn một registry sống lâu chứa hàng nghìn class loader.

## 14. Generic methods, bridge, default interface methods

Cần thận trọng khi method resolution không đơn giản:

### 14.1. Generic erasure và bridge methods

Một implementation của interface generic có thể khiến Java compiler tạo **bridge method** synthetic để duy trì polymorphism sau erasure. Một bộ quét metadata có thể thấy bridge và method implementation, trong khi annotation nằm ở declaration cụ thể khác.

Đừng mặc định `getMethods()` rồi lấy method đầu tiên cùng tên là đúng. Cần xét `Method.isBridge()`, `isSynthetic()`, parameter types, declaring class và các quy tắc của công cụ đang dùng.

### 14.2. Default interface methods

Một default method có body trên interface. Tùy trường hợp class không override, lời gọi có thể dùng implementation của interface. Resolver dựa trên `getMethod` có thể nhận một `Method` do interface khai báo chứ không phải “method viết tại target class”.

Quy tắc precedence vẫn phải đọc được annotation đúng declaration, không suy mọi `getMethod` đều trả method do class triển khai tự khai báo.

### 14.3. Method overrides

Một method override trên subclass có thể mang annotation khác hoặc không mang annotation nào. Java method annotations thông thường không được kế thừa tự động theo mọi override. Nếu muốn fallback tới superclass method, phải tự định nghĩa điểm dừng và thứ tự tìm kiếm.

Với nhiều interfaces kế thừa nhau, cũng phải chọn declaration có ý nghĩa. Nếu hai interface đặt cấu hình trái nhau, nên phát hiện conflict hơn là chọn âm thầm một nguồn có thứ tự không được công bố.

## 15. Module, access và class-loader

Một runtime có Java Platform Module System (**JPMS**) có thể giới hạn access. `exports` cho phép truy cập public API ở biên module theo quy tắc; `opens` liên quan tới deep reflection. Hai cơ chế khác nhau.

Một annotation type hoặc target method không accessible từ module đang thực hiện lookup/invocation có thể tạo lỗi liên kết hoặc phản chiếu. `setAccessible(true)` không bảo đảm luôn thành công dưới module mạnh.

Trong môi trường nhiều class loader, hai class có **cùng binary name** nhưng do những loader khác nhau định nghĩa có thể là hai runtime types khác nhau. Khi đó:

```java
method.getAnnotation(Observe.class)
```

có thể không thấy annotation type người đọc mong đợi nếu `Observe.class` thuộc một loader/phiên bản khác. Không chỉ so tên annotation dạng chuỗi rồi cast tùy tiện sang kiểu trong loader khác.

Điểm kiểm chứng:

- Annotation type có được tải bằng loader đúng không?
- Target class có được weaver/proxy xử lý đúng không?
- Method `public` có thực sự accessible xuyên module?
- Artifact có giữ runtime annotation trong bytecode không?
- Có lẫn class đã weave và chưa weave không?
- Cache metadata có làm giữ class loader cũ không?

## 16. Đọc cấu hình từ annotation không phải cấu hình động từ xa

Annotation thường được **đóng vào class file**. Muốn đổi `@Observe(category="billing")` thành `"warehouse"` ở một implementation đã biên dịch, thông thường phải **đổi mã/bytecode rồi build/deploy tương ứng**, trừ khi công cụ có cơ chế biến đổi đặc thù.

Một hệ quản trị cấu hình động có thể kết hợp metadata với giá trị từ nơi khác:

```text
Annotation declaration:
    policyId = "invoice-tracing"

Runtime policy registry:
    invoice-tracing -> sampling = 10%, enabled = true
```

Đây là sơ đồ kiến trúc, không phải Java. Trong mô hình này, annotation chỉ là **khóa lựa chọn chính sách**, còn giá trị động đến từ registry. Phải công bố thứ tự ưu tiên, version, lỗi không khả dụng và mức tin cậy. Không tự nói “đọc annotation trong advice” đồng nghĩa đọc giá trị mới nhất từ service cấu hình.

Với cấu hình bảo mật quan trọng, dùng một service động còn cần xác thực, integrity, cache freshness và fallback fail-safe; annotation đơn lẻ không đảm bảo những điều đó.

## 17. Lỗi thường gặp và cách nhận diện

| Sai lầm | Biểu hiện | Cách giải quyết |
| --- | --- | --- |
| Thiếu `@Retention(RUNTIME)` | `getAnnotation` trả null dù source có annotation | Đặt retention đúng khi cần reflection |
| Dùng sai `@Target` | Annotation không gắn được nơi mong muốn | Định nghĩa METHOD/TYPE/PARAMETER phù hợp |
| Chỉ đọc interface method | Bỏ mất cấu hình ở implementation | Resolve implementation và công bố precedence |
| Chỉ đọc implementation method | Bỏ mất annotation ở interface | Fallback có chủ đích nếu hợp đồng cho phép |
| Coi `@Inherited` áp cho overridden methods | Cấu hình bị thiếu | Lookup method hierarchy rõ ràng |
| Coi meta-annotation tự được tìm đệ quy | `getAnnotation` trả null | Viết resolver/meta-lookup hoặc dùng công cụ đã xác minh |
| Đọc repeatable annotation bằng một `getAnnotation` | Thiếu các lần khai báo | Dùng `getAnnotationsByType` và merge rule |
| Chỉ so tên method | Lấy metadata sai overload | Dùng parameter types/signature |
| Bỏ qua bridge/synthetic method | Nhìn nhầm declaration hoặc metadata | Xử lý resolver đúng ngữ nghĩa |
| Không kiểm tra giá trị mặc định | Category trống, timeout âm, cardinality tăng | Validation ở build/startup/runtime |
| Dùng `logReturn=true` với dữ liệu nhạy cảm | Rò dữ liệu trong log | Redaction, allowlist, policy review |
| Annotation tùy ý tắt guard bắt buộc | Mất authorization/audit | Chính sách từ nguồn đáng tin, fail closed |
| Bọc mọi `proceed` trong catch “logger offline” | Nuốt lỗi target | Catch riêng nguồn lỗi observer |
| Coi proxy tự can thiệp self-invocation | Advice không chạy | Xác minh đường qua proxy/weaver |
| Cache theo tên method | Metadata sai giữa overload/classes | Khóa cache đủ chính xác |
| Cache class/Method trong global map vô hạn | Class-loader retention | Lifetime và eviction hoặc ClassValue |
| Tin annotation tự là dynamic config | Đổi config runtime không có hiệu lực | Tách declaration và registry |
| Không test deployment artifact | Runtime thiếu metadata/weaving | Kiểm tra đúng class file, loader và toolchain |

## 18. Ma trận kiểm thử advice đọc annotation

| Kịch bản | Kỳ vọng |
| --- | --- |
| Method có annotation RUNTIME ở implementation | Đọc đúng giá trị phần tử |
| Method chỉ annotate ở interface | Fallback đúng khi chính sách yêu cầu |
| Cả interface lẫn implementation annotate | Chọn precedence đã công bố |
| Method không annotate | Không áp observer nếu đó là contract |
| Method annotate với defaults | Giá trị default đúng |
| Method có category rỗng | Báo lỗi cấu hình hoặc degrade theo policy |
| Annotation thiếu RUNTIME retention | Reflection không thấy, không tự giả có |
| Nhiều overload cùng tên | Không đọc nhầm annotation |
| Override superclass annotation | Có/không fallback đúng theo quy tắc |
| Repeatable annotations | Đọc và hợp nhất đúng tất cả lần gắn |
| Meta-annotation | Chỉ đọc nếu resolver hỗ trợ đã định nghĩa |
| Method có `logReturn=false` | Không ghi result trong advice |
| Method có `logReturn=true` | Chỉ ghi result khi an toàn theo policy |
| Target return bình thường | Giá trị không bị đổi bởi observer |
| Target throw | Lỗi gốc không bị che |
| Logger/exporter lỗi | Observer handling không nuốt target error |
| Hai aspects cùng match | Precedence/nesting chính xác |
| Proxy bypass | Direct target không tự kích hoạt advice |
| Async method trả future | Phân biệt method return và future completion |
| Multi-thread | Metadata cache/target state an toàn |
| Class/module loader thay đổi | Lookup không giữ nhầm phiên bản |
| Artifact sau weaving | Annotation/advice hiện diện ở build thực |

### 18.1. Test âm quan trọng không kém test dương

Chỉ assert rằng method `save` có log chưa đủ. Cần assert rằng method `plain` **không** phát advice message, `fetch` **không** bị log result nếu annotation không cho phép, method khác với cùng tên nhưng chữ ký khác không bị chọn nhầm, và lỗi target vẫn đến đúng caller.

### 18.2. Test giá trị không phải chỉ test sự tồn tại

Một test kiểu `assertNotNull(getAnnotation(...))` không chứng minh `category`, `tags`, `logReturn` đúng hoặc quy tắc implementation-first được tôn trọng. Hãy assert **giá trị từng element và hành vi advice sau khi dùng cấu hình đó**.

### 18.3. Test trên đường gọi thực tế

Nếu dùng JDK proxy, test phải gọi **qua proxy**; nếu dùng weaver, test phải chạy trên **artifact đã được weave**. Cần thử đường gọi trực tiếp target và self-invocation để phát hiện phạm vi không được chặn. “Annotation tồn tại trong class file” và “advice thực sự chạy” là **hai sự thật riêng**.

## 19. Checklist thiết kế

1. Annotation có **mục đích và owner** rõ không?
2. `@Target` có đúng loại declaration advice cần xét không?
3. `@Retention` đủ cho giai đoạn truy xuất (runtime hay build) không?
4. Các annotation elements có kiểu, default và ràng buộc rõ không?
5. Pointcut chọn theo **method/interface/implementation/type** nào?
6. Resolver có quy tắc **precedence và fallback** công khai không?
7. Có trường hợp nhiều interfaces hoặc method overrides conflict không?
8. Có repeatable/meta/inherited annotation nào cần xử lý riêng không?
9. Values có phải **đề nghị quan sát** hay **chính sách bắt buộc**?
10. Advice có áp giới hạn dữ liệu nhạy cảm trước khi in args/result không?
11. Lỗi của observer có được tách khỏi lỗi target không?
12. Annotation có thể làm thay đổi retry/transaction/authorization ngoài ý muốn không?
13. Lookup reflection/caching có đúng overload, class loader và module không?
14. Có theo dõi phiên bản annotation/schema và binary compatibility không?
15. Có test return/throw, method không annotate, override và direct-target bypass không?
16. Có chạy test bằng toolchain/artifact **thật** thay vì chỉ bằng snippet đơn lẻ không?

## 20. Bài tập vận dụng và đáp án

### Bài 1 — Annotation không xuất hiện runtime

Một method có `@TraceRule` trong source nhưng `method.getAnnotation(TraceRule.class) == null`. Nguyên nhân đầu tiên cần kiểm tra?

**Đáp án:** `@Retention(RetentionPolicy.RUNTIME)` của annotation type, rồi đến method declaration thực sự được phản chiếu. Mặc định retention CLASS không bảo đảm reflection runtime đọc được.

### Bài 2 — Interface và implementation

`Gateway.save` ở interface có `@Observe(category="A")`, `GatewayImpl.save` có `@Observe(category="B")`. Theo contract implementation-first của ví dụ, category nào được dùng?

**Đáp án:** `B`. Resolver ưu tiên method của implementation; chỉ khi không có annotation mới dùng interface. Đây là quyết định thiết kế, không phải quy tắc tự động của mọi AOP.

### Bài 3 — Không có annotation

Method không mang `@Observe` ở bất kỳ nơi nào được quy tắc lookup xét. Advice quan sát có nên in log không?

**Đáp án:** Trong contract của ví dụ, không. Nó chuyển thẳng tới target. Nhưng nếu một chính sách bắt buộc phải áp mọi method nhạy cảm, **vắng annotation** không nên tự cho phép bypass.

### Bài 4 — Mặc định và cấu hình tường minh

`@Observe` có `logReturn() default false`. Có khác `@Observe(logReturn=false)` khi advice gọi `cfg.logReturn()` không?

**Đáp án:** Không khác ở **giá trị đọc được**. Cả hai trả false. Nếu cần phân biệt intent “không chỉ định”, phải có contract bổ sung.

### Bài 5 — Method overrides

Interface method annotate, implementation method không. Vì sao `implementationMethod.getAnnotation(...)` có thể trả null?

**Đáp án:** Java method annotation không được tự kế thừa từ interface declaration sang implementation. Resolver cần fallback nếu policy cho phép.

### Bài 6 — Annotation lặp

Method có hai `@Tag` repeatable. Tại sao chỉ gọi `getAnnotation(Tag.class)` có thể bỏ sót?

**Đáp án:** Vì nhiều annotation được biểu diễn qua repeatable/container semantics. Dùng `getAnnotationsByType(Tag.class)` nếu cần cả hai và quy định cách merge.

### Bài 7 — An toàn log

Method trả một object chứa credential, annotation đặt `logReturn=true`. Có được in `result.toString()` ngay không?

**Đáp án:** Không. Flag không thay thế kiểm soát dữ liệu. Cần giới hạn loại result, redaction và quy tắc log theo chính sách; có thể từ chối logging khi dữ liệu không an toàn.

### Bài 8 — Kiểu annotation và lỗi cấu hình

Method gắn `@Observe(category="")`. Vì sao compiler vẫn có thể chấp nhận nhưng advice nên báo lỗi?

**Đáp án:** Chuỗi rỗng vẫn là `String` hợp kiểu; ràng buộc “category không trống” là bất biến nghiệp vụ của annotation, cần validation riêng.

### Bài 9 — Java 17 demo

Vì sao `fetch("A1")` có dòng `ADVICE before read` nhưng không có `ADVICE return value:A1`?

**Đáp án:** Interface có annotation category read, implementation không annotate nên fallback dùng interface. `logReturn()` mặc định false, nên advice không in result; caller vẫn nhận và in `CALL value:A1`.

### Bài 10 — Lỗi target

`reject("C3")` ném `IllegalArgumentException`. Vì sao caller nhận đúng ngoại lệ thay vì `InvocationTargetException`?

**Đáp án:** Helper gọi reflection bắt wrapper `InvocationTargetException` và ném `getCause()`, còn advice chỉ quan sát và rethrow. `finally` vẫn chạy.

### Bài 11 — Meta-annotation

`@Critical` có `@Observe` gắn trên annotation type. Vì sao `method.getAnnotation(Observe.class)` không tự bảo đảm tìm được Observe?

**Đáp án:** Reflection không có một quy tắc quét meta-annotation tùy ý nhiều tầng cho mọi use case. Cần thiết kế resolver hoặc dùng công cụ có semantics đã xác minh.

### Bài 12 — Nhiều interface cùng method

Hai interfaces khai báo `save(String)` nhưng annotate khác nhau. Có nên coi handler biết chắc caller đã cast qua interface nào không?

**Đáp án:** Không. JDK Proxy xử lý duplicate signatures theo quy tắc riêng; một handler không nên suy ý nghĩa caller từ cast. Phải định nghĩa một contract thống nhất hoặc phát hiện conflict.

### Bài 13 — Cấu hình runtime

Bạn thay file YAML của ứng dụng nhưng `@Observe(category="old")` trong class file không đổi. Vì sao advice vẫn đọc `old`?

**Đáp án:** Annotation đi cùng declaration đã biên dịch, không phải một liên kết tới file cấu hình từ xa. Nếu muốn dynamic policy, phải xây một nguồn runtime riêng và quy tắc merge/version.

### Bài 14 — Proxy bypass

Một lớp có annotation ở `save` nhưng caller giữ target gốc thay vì proxy. Annotation có đủ làm advice tự chạy không?

**Đáp án:** Không. Annotation chỉ là metadata; cơ chế interception cần thực sự bao phủ đường gọi. Trong ví dụ JDK proxy, gọi trực tiếp target không chạy advice.

## 21. Ghi nhớ

- **Đọc annotation trong advice cần xác định đúng declaration, đúng retention và đúng hợp đồng sử dụng giá trị**, không chỉ gọi `getAnnotation` rồi tin kết quả.
- **RUNTIME retention** quan trọng cho Java reflection; `@Target` phải phù hợp method/type/parameter thực sự được xem.
- **Interface method khác implementation method**; annotations của chúng không tự được hợp nhất. Fallback/precedence phải được công bố và kiểm thử.
- **`@Inherited` không tự truyền method annotations**; repeatable và meta-annotations cần APIs/quy tắc riêng.
- **Giá trị annotation là metadata có kiểu**, không tự là dynamic config từ xa hay quyền bỏ qua một chính sách bảo mật.
- **Advice có thể bind annotation bằng pointcut hoặc lookup bằng reflection** khi ngôn ngữ/công cụ cho phép; selection và resolution phải thống nhất.
- **Không log kết quả nhạy cảm chỉ vì flag cho phép**; validation, data classification và failure contract là bắt buộc về mặt thiết kế.
- Reflection caching cần khóa đủ chính xác, thread safety và quản lý class loader; không tối ưu quá sớm.
- **Proxy/weaving coverage** quyết định advice có chạy hay không; annotation hiện diện trong class file không tự chứng minh join point đã được can thiệp.
- Test **positive/negative, method variants, inheritance, return/throw, proxy bypass, metadata version và artifact runtime thật**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="annotation-metadata-007">Kết hợp marker tường minh với giới hạn phạm vi</a>

<details>
<summary>Xem chi tiết</summary>

# Kết hợp marker tường minh với giới hạn phạm vi

## 1. Vì sao chỉ dùng marker hoặc chỉ dùng phạm vi thường chưa đủ?

Trong **Aspect-Oriented Programming (AOP)**, một **pointcut** xác định tập **join point** mà advice có thể can thiệp. Với những cơ chế AOP cho phép chọn điểm dựa trên annotation/metadata, ta có thể đánh dấu trực tiếp một method hoặc type bằng một **marker** để nói rằng chúng thuộc một concern. Đồng thời, ta có thể giới hạn phạm vi chọn theo module, package, type role, API boundary, component hoặc ngữ cảnh thực thi.

Hai chiều này giải quyết **hai câu hỏi khác nhau**:

- **Marker tường minh:** “Điểm này được người sở hữu code tuyên bố thuộc concern nào?” Ví dụ `@ObservedOperation` biểu thị một operation cần metrics/tracing.
- **Giới hạn phạm vi (scope restriction):** “Aspect này được phép hoặc được thiết kế hoạt động ở khu vực nào của chương trình?” Ví dụ chỉ trong một module công khai, các API thuộc role giao dịch, hoặc các objects thuộc trust boundary xác định.

Nếu chỉ chọn theo marker, một method debug hoặc helper nội bộ vô tình gắn annotation giống method nghiệp vụ có thể bị advice xử lý, khiến logging quá nhiều, rò dữ liệu hoặc retry sai. Nếu chỉ chọn theo package/role, aspect có thể chạm tới mọi method trong vùng, kể cả health check, getter, helper, hoặc lệnh có semantics hoàn toàn khác. **Kết hợp cả hai** cho phép biểu diễn chính sách rõ hơn.

> **Quy tắc tư duy:** `SELECT = MARKED && IN_SCOPE && SUPPORTED_JOIN_POINT && NOT_EXCLUDED`. Mỗi điều kiện là một phần của hợp đồng selection, không phải một lời đảm bảo rằng method chắc chắn đã được intercept hoặc policy nghiệp vụ đã đúng.

Ví dụ về tập được chọn:

| Join point | Có `@ObservedOperation`? | Thuộc scope công bố? | Có được chọn? |
| --- | --- | --- | --- |
| `BillingApi.charge()` | Có | Có | **Có**, nếu join point được công cụ hỗ trợ. |
| `BillingApi.health()` | Không | Có | Không. |
| `InternalMaintenance.cleanup()` | Có | Không | Không. |
| `OtherService.ping()` | Không | Không | Không. |

### Sau bài học bạn có thể

1. Phân biệt marker annotation, type-level scope, package/module scope, call/execution join point và runtime contextual filter.
2. Viết mô hình logic cho các điều kiện **AND / OR / NOT** mà không mắc lỗi precedence.
3. Hiểu `@Retention`, `@Target`, vị trí annotation trong interface/class và giới hạn `@Inherited`.
4. Thiết kế explicit positive selection đi kèm giới hạn domain/module, tránh overmatching từ wildcard.
5. Tránh bỏ sót do selector thiếu metadata, do proxy bypass hoặc do scope di chuyển theo refactor.
6. Phân tích trade-off: interface role vs package; declaration scope vs runtime scope; annotation trên API vs implementation.
7. Bảo vệ security, retry, caching và audit trước những thay đổi ngoài ý muốn của matched set.
8. Chạy ví dụ Java 17 dùng JDK dynamic proxy, với ba trường hợp **đủ cả hai điều kiện / chỉ scope / chỉ marker**.
9. Lập positive/negative coverage tests, test error paths, nested flows và async để chứng minh sự đúng đắn.
10. Có checklist để review việc thêm, xóa, đổi marker hoặc di chuyển một class sang scope khác.

**Phạm vi bài:** Những nguyên lý Aspect/AOP cơ bản và nâng cao nói chung. Cú pháp pointcut, annotation lookup và giới hạn join-point kinds phụ thuộc công cụ; các sơ đồ matcher dưới đây là **giả mã khái niệm** trừ khi được ghi là Java 17 chạy được.

## 2. Mô hình toán học cho selection: giao giữa hai tập

Gọi:
- \(J\): tập join points mà công cụ AOP có thể thấy.
- \(M\): tập join points có marker theo **metadata lookup rule** đã chọn.
- \(S\): tập join points trong phạm vi được phép.
- \(E\): tập join points cần loại trừ tường minh.
- \(C\): tập join points có thể thực sự được can thiệp trên những đường gọi hiện có.

Tập dự kiến:

\[
P = J \cap M \cap S \setminus E
\]

Tập **thực tế advice nhận** còn phụ thuộc cơ chế interception:

\[
A = P \cap C
\]

Với một proxy, `C` thường bao gồm các invocations đi **qua đúng proxy**, không tự gồm direct target calls và self-invocation. Với một cơ chế weaving, `C` lại chịu phạm vi class/join-point weaving và runtime artifact.

Điều kiện `MARKED && IN_SCOPE` vẫn chỉ là **mô hình selection**; nó không tự bảo đảm mọi caller đều qua aspect.

### 2.1. Marker là positive intent

Marker có thể là annotation đặt trên method/type, một named role đã được công bố, hay metadata được quản trị. Trong ví dụ Java, marker là annotation thật để minh họa tính tường minh:
 
~~~java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface ObservedOperation { }
~~~

Đây là **đoạn minh họa**; cần imports và enclosing context để biên dịch. Khi compiler thấy marker trên declaration, người đọc source có thể hiểu rằng operation này được chọn cho observability theo convention của dự án.

### 2.2. Scope là một ranh giới độc lập

Scope không nên được hiểu mơ hồ là “mọi thứ có cùng marker”. Nó có thể là:
- Một API/interface role ổn định.
- Một module đã được xác nhận theo ownership.
- Một package boundary thực sự được enforce.
- Một set của các target types.
- Một tập join-point kinds có ý nghĩa (execution thay vì call).
- Một dynamic control flow được hỗ trợ bởi engine.
- Một policy chỉ được phép áp ở một nơi tin cậy.

Một type có marker vẫn có thể **ngoài scope**. Ngược lại, một method trong scope vẫn bị loại nếu không đánh dấu.

### 2.3. Exclusion không nên thay cho positive boundary

Có thể định nghĩa một danh sách rất dài `NOT helper && NOT logger && NOT health && ...`. Nhưng nếu positive selector là “mọi method trong application”, một component mới có thể vô tình bị chọn trước khi được xem xét. Dùng **positive intent + scope đủ hẹp**, rồi thêm exclusions cho những ngoại lệ đã được xác định.

## 3. Những đơn vị phạm vi thường dùng

| Phạm vi | Ví dụ | Ưu điểm | Cẩn trọng |
| --- | --- | --- | --- |
| **API interface** | `BillingApi`. | Compile-time role rõ, đổi implementation không nhất thiết đổi scope. | Proxy/execution matching và inherited metadata phải kiểm tra. |
| **Type annotation** | `@BusinessBoundary` trên interface/class. | Chủ đích explicit; di chuyển package ít ảnh hưởng. | Annotation cũng cần governance. |
| **Module** | Chỉ thuộc module nghiệp vụ được công bố. | Phù hợp ownership/deployment. | Module graph, classloading, exports/opens. |
| **Package** | `com.example.billing.api`. | Dễ diễn đạt khi package thật là architecture boundary. | Rename/move và overmatching. |
| **Subtype relationship** | Implement một role interface. | Tập trung vào capability. | Có thể quá rộng nếu interface đa mục đích. |
| **Join-point kind** | Chỉ method execution. | Ngăn nhầm call với execution hoặc field access. | Engine support khác nhau. |
| **Dynamic context** | Bên trong logical `checkout()` flow. | Gắn với operation đang diễn ra. | Async, nesting, ThreadLocal/context propagation. |
| **Tenant/request policy** | Tenant được xác thực ở trusted entrypoint. | Context-aware. | Không lấy security context từ untrusted marker/headers. |

### 3.1. Type-level scope không đồng nghĩa per-instance aspect scope

Một type được đánh dấu `@BusinessBoundary` nghĩa là selector nhận diện một **loại API**; nó không có nghĩa mỗi object của type tạo ra một aspect instance mới. Việc aspect state là singleton/per-target/per-flow/per-invocation là **instantiation model** độc lập.

### 3.2. Package scope cần bằng chứng kiến trúc

`com.example.billing.*` có thể là chính sách hợp lý nếu package được quản trị như boundary và có architecture tests. Nhưng nếu package chỉ là nơi tiện để lưu file, dùng nó làm security/audit boundary có thể giòn. Khi class bị di chuyển, cần regression test chứng minh matched set không đổi hoặc đổi có chủ đích.

### 3.3. Module scope khác Java package name

Java module system có thể kiểm soát exports/opens, còn package là namespace. Một matcher nhìn package có thể vẫn nhận tên type, nhưng reflection/bytecode weaving có thể chịu module access limitations. **“Khớp tên”** và **“có thể thực thi advice”** là hai điều khác nhau.

## 4. Marker tường minh là một contract, không phải phép màu

Một marker `@ObservedOperation` trả lời “developer **muốn** operation này được đo”. Nó không tự nói:
- Method có an toàn để retry không?
- Có được phép ghi toàn bộ arguments không?
- Authorization đã thực sự diễn ra ở trusted boundary chưa?
- Method là synchronous hay async?
- Một call qua proxy có đi tới method được marked không?
- Annotations có được đặt đúng nơi engine lookup không?
- Log sink fail thì có được làm business call fail không?

### 4.1. Marker nên có một ý nghĩa hẹp

Nếu marker `@Managed` đồng thời bật tracing, cache, retry, masking, transaction và audit, thêm annotation tại một method mới có thể kích hoạt rất nhiều side effects. Hãy ưu tiên một vocabulary riêng cho từng policy:
- `@ObservedOperation`: observation.
- `@AuditedMutation`: event audit theo hợp đồng.
- `@RetryAllowed`: chỉ khi operation được xác nhận idempotent/eligible.
- `@Sensitive`: policy xử lý thông tin cần masking.

Những tên trên là ví dụ thiết kế, không phải API chuẩn AOP. Marker **phải có documentation và tests** cho semantics cụ thể.

### 4.2. Marker có thể là class-level hoặc method-level

- **Class-level:** mọi operations của một type có thể được xem là candidates. Dễ quản trị theo role nhưng có nguy cơ quá rộng.
- **Method-level:** developer chọn từng operation. Granular hơn nhưng có rủi ro quên đánh dấu.
- **Parameter-level:** hữu ích cho masking/binding argument metadata, nhưng không tự có nghĩa cả method được chọn.
- **Type-use annotation:** mô tả kiểu sử dụng tại vị trí hợp lệ, không nhất thiết là một join-point selector.

Nếu muốn cả type và method xác nhận, có thể dùng hai markers: ví dụ `@BusinessBoundary` ở interface, `@ObservedOperation` ở method. Đây chính là mẫu **AND** trong bài.

### 4.3. Marker thiếu có phải fail-closed?

Phụ thuộc concern:
- Metrics optional: thiếu marker có thể chỉ dẫn tới không đo, nhưng phải phát hiện bằng coverage test.
- Audit bắt buộc: thiếu marker có thể là vi phạm hợp đồng nghiêm trọng; cần lint/test hoặc đặt enforcement ở trusted owner.
- Authorization: tuyệt đối không coi sự có mặt của marker là **toàn bộ** trust boundary. Caller có thể đi đường bypass interceptor.

Marker mang **intent**, không thay domain/security invariants.

## 5. Java annotation: Retention, Target, Inherited và metadata lookup

Khi chọn theo **marker AND phạm vi**, không chỉ hỏi “có annotation không?” mà phải định nghĩa rõ **marker nằm ở đâu** và **scope được đánh giá trên type nào**. Nếu hai phép tra cứu dùng hai declaration khác nhau, phép giao `M ∩ S` có thể chọn sai tập join point.

| Quy tắc | Câu hỏi cần quyết định cho phép giao marker/scope |
| --- | --- |
| `RetentionPolicy` | Matcher chạy bằng reflection (`RUNTIME`) hay dùng source/classfile ở bước weaving? |
| `@Target` | Marker đặt trên method, type hay parameter? Scope marker có cùng cấp với operation marker không? |
| `@Inherited` | Có dựa vào superclass type không? **Không** tự thừa kế method/interface annotations. |
| Method lookup | Proxy cung cấp `Method` trên interface hay execution matcher thấy implementation method? |
| Repeatable/meta markers | Consumer có chủ động unpack repeatable annotations và tìm meta-annotations không? |

Ví dụ, nếu `@ObservedOperation` đặt ở **interface method** nhưng matcher chỉ kiểm `implementationMethod.isAnnotationPresent(...)`, giá trị “marker” có thể false dù use case được đánh dấu đúng contract. Cần quy định và test **interface → implementation lookup** theo chiều đã chọn; Java reflection không tự merge một bộ annotation duy nhất của mọi interface, superclass và override. Tương tự, một type-level scope marker đặt trên interface không tự thành annotation trên concrete implementation.

**Thiết kế thực tế:** chốt method đang xét, resolve marker + scope thành hai giá trị Boolean theo cùng một chính sách metadata, sau đó áp `AND` (và exclusions) có kiểm thử. Không đánh đồng annotation hợp lệ với **coverage** của AOP engine; dù điều kiện đúng, lời gọi direct target vẫn có thể bypass một delegation proxy.

## 6. Kết hợp Boolean: AND, OR, NOT cho pointcut

Mô hình:

~~~text
# Giả mã, không phải cú pháp chuẩn của một AOP engine.
select(
    isMethodExecution
    AND hasMarker(ObservedOperation)
    AND inScope(BusinessBoundary)
    AND NOT isInfrastructureMethod
)
~~~

### 6.1. Các trường hợp sử dụng

- **AND:** chỉ chọn method được annotated **và** thuộc approved boundary.
- **OR:** chấp nhận một trong các scopes đã được công bố, ví dụ `BillingApi OR ShippingApi`.
- **NOT:** loại một method/role đặc biệt, ví dụ `healthCheck` hoặc `TelemetryExporter`.
- **Context predicate:** chỉ kích hoạt khi có một flow context hợp lệ, theo khả năng engine.

### 6.2. Dấu ngoặc rất quan trọng

Hai công thức này không tương đương:

~~~text
(MARKED AND BILLING) OR SHIPPING

MARKED AND (BILLING OR SHIPPING)
~~~

Công thức đầu **chọn mọi method trong SHIPPING** dù không marked; công thức thứ hai đòi marker ở cả hai scopes. Một selector bị sai thứ tự `AND/OR` có thể gây **overmatching** nghiêm trọng.

Vì cú pháp và precedence của các pointcut languages khác nhau, hãy dùng grouping tường minh và viết tests cho cả positive/negative cases, không rely vào trí nhớ về precedence.

### 6.3. Overmatching và undermatching đều nguy hiểm

- **Overmatching:** một retry advice áp vào command không idempotent; logging đọc secrets; transaction scope mở sai.
- **Undermatching:** telemetry thiếu, audit bắt buộc không được emit, authorization policy bị bypass nếu đây là nơi duy nhất enforce.
- **Matching sai đơn vị:** metrics tưởng đo một logical operation nhưng thực tế đếm helper attempts; async task success bị ghi khi method return stage.

Định nghĩa **đơn vị quan sát** trước khi hoàn thiện selector.

## 7. Ví dụ Java 17 runnable: method marker AND interface scope

Ví dụ này cho thấy cách tổ hợp hai dấu hiệu selection trên **JDK dynamic proxy**. Nó là ví dụ về **AOP-like interception** trong Java thuần, không mô phỏng mọi kiểu weaving.

Điều kiện để ghi `ADVICE` là:
1. Interface view truyền vào proxy có `@BusinessArea` ở **type level**.
2. Interface method có `@ObservedOperation` ở **method level**.

Chúng ta chuẩn bị ba test cases:
- `BillingApi.charge`: **marker có + scope có**, được quan sát.
- `BillingApi.health`: **marker không + scope có**, không được quan sát.
- `InternalApi.cleanup`: **marker có + scope không**, không được quan sát.

Thêm một lỗi target `charge("BAD")` để kiểm tra marker logic vẫn áp trên failure path và handler truyền lại exception gốc.

Tạo file **`MarkerScopeDemo.java`**:

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

public class MarkerScopeDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface BusinessArea { }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface ObservedOperation { }

    @BusinessArea
    public interface BillingApi {
        @ObservedOperation
        String charge(String id);

        String health();
    }

    public interface InternalApi {
        @ObservedOperation
        String cleanup(String id);
    }

    static final class BillingTarget implements BillingApi {
        @Override
        public String charge(String id) {
            System.out.println("TARGET charge " + id);
            if ("BAD".equals(id)) {
                throw new IllegalArgumentException("bad id");
            }
            return "PAID:" + id;
        }

        @Override
        public String health() {
            System.out.println("TARGET health");
            return "UP";
        }
    }

    static final class InternalTarget implements InternalApi {
        @Override
        public String cleanup(String id) {
            System.out.println("TARGET cleanup " + id);
            return "CLEAN:" + id;
        }
    }

    static <T> T observed(
            Class<T> view,
            T target,
            AtomicInteger selectedCount) {
        if (!view.isInterface() || !view.isInstance(target)) {
            throw new IllegalArgumentException("invalid interface view");
        }

        boolean inScope = view.isAnnotationPresent(BusinessArea.class);

        Object proxy = Proxy.newProxyInstance(
                view.getClassLoader(),
                new Class<?>[] { view },
                (proxyObject, method, arguments) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "equals" -> proxyObject == arguments[0];
                            case "hashCode" ->
                                    System.identityHashCode(proxyObject);
                            case "toString" -> "MarkerScopeProxy";
                            default -> throw new UnsupportedOperationException(
                                    method.getName());
                        };
                    }

                    boolean selected = inScope
                            && method.isAnnotationPresent(
                                    ObservedOperation.class);

                    if (selected) {
                        selectedCount.incrementAndGet();
                        System.out.println(
                                "ADVICE before " + method.getName());
                    }

                    try {
                        Object result = method.invoke(target, arguments);
                        if (selected) {
                            System.out.println(
                                    "ADVICE after " + method.getName());
                        }
                        return result;
                    } catch (InvocationTargetException wrapped) {
                        Throwable cause = wrapped.getCause();
                        if (selected) {
                            System.out.println(
                                    "ADVICE error " + method.getName()
                                    + " "
                                    + cause.getClass().getSimpleName());
                        }
                        throw cause;
                    }
                }
        );

        return view.cast(proxy);
    }

    public static void main(String[] args) {
        AtomicInteger matches = new AtomicInteger();
        BillingApi billing =
                observed(BillingApi.class, new BillingTarget(), matches);
        InternalApi internal =
                observed(InternalApi.class, new InternalTarget(), matches);

        System.out.println("CHARGE=" + billing.charge("A"));
        System.out.println("HEALTH=" + billing.health());
        System.out.println("INTERNAL=" + internal.cleanup("X"));

        try {
            billing.charge("BAD");
        } catch (IllegalArgumentException failure) {
            System.out.println("ERROR=" + failure.getMessage());
        }

        System.out.println("SELECTED=" + matches.get());
    }
}
~~~

Biên dịch và chạy bằng JDK 17:

~~~bash
javac MarkerScopeDemo.java
java MarkerScopeDemo
~~~

**Stdout kỳ vọng, đúng thứ tự:**

~~~text
ADVICE before charge
TARGET charge A
ADVICE after charge
CHARGE=PAID:A
TARGET health
HEALTH=UP
TARGET cleanup X
INTERNAL=CLEAN:X
ADVICE before charge
TARGET charge BAD
ADVICE error charge IllegalArgumentException
ERROR=bad id
SELECTED=2
~~~

### 7.1. Vì sao `charge("A")` được chọn?

`observed(BillingApi.class,...)` xác nhận `BillingApi` có `@BusinessArea` nên `inScope=true`. Khi client gọi `billing.charge("A")`, JDK proxy handler nhận interface `Method` có `@ObservedOperation`, nên `selected=true`. Handler tăng `matches`, in dòng before, gọi target, rồi in after. Caller nhận đúng `PAID:A`.

Không có điều kiện nào dựa vào tên package/concrete class `BillingTarget`; đối tượng mới implement `BillingApi` có thể đi qua cùng policy của factory nếu giữ hợp đồng interface ấy.

### 7.2. Vì sao `health()` bị loại dù cùng scope?

`BillingApi` ở trong scope, nhưng method `health` **không có marker**. `inScope && marked` là false. Proxy vẫn **delegate bình thường** tới target, in `TARGET health`, nhưng không có dòng advice. Đây là negative case chứng minh **scope đơn lẻ không đủ**.

### 7.3. Vì sao `cleanup()` bị loại dù có marker?

`InternalApi.cleanup` có marker, nhưng `InternalApi` **không** có `@BusinessArea`. Vì thế `inScope=false`; dù method được annotated, `selected=false`. Target vẫn chạy, nhưng không có instrumentation. Đây là negative case chứng minh **marker đơn lẻ không đủ**.

### 7.4. Vì sao `SELECTED=2`?

Có hai invocations được chọn: `billing.charge("A")` và `billing.charge("BAD")`. `health()` và `internal.cleanup()` không được chọn. Bộ đếm tăng **trước** khi gọi target, nên nó phản ánh **attempted selected calls**, không phải số lần thành công.

Muốn phân biệt success và failure, dùng counters riêng trong đường return/throw, đặt policy đo rõ theo business logic.

### 7.5. Lỗi từ target có còn nguyên loại không?

Khi target ném `IllegalArgumentException("bad id")`, reflective `Method.invoke` bọc exception trong `InvocationTargetException`. Handler lấy cause, in log failure rồi `throw cause`, nên caller bắt được `IllegalArgumentException`. Điều kiện selection không thay hợp đồng lỗi của target.

### 7.6. Code này giới hạn được điều gì?

Nó giới hạn **instrumentation ở interface proxy boundary**, không thay class `BillingTarget` hay `InternalTarget`. Nếu caller giữ target và gọi trực tiếp, handler không chạy. Nếu target tự gọi `this.charge(...)`, invocation ấy cũng không bắt buộc đi qua proxy. Nó không dùng bytecode weaving hay pointcut DSL của một AOP engine.

### 7.7. Những giả định quan trọng của demo

- Chỉ có một interface trong mỗi proxy, nên method-resolution không có ambiguity do duplicated methods từ nhiều interfaces.
- Annotation lookup cố ý chỉ đọc **declaration trên interface** được pass vào `observed`, không merge implementation annotations.
- `BillingApi` và `InternalApi` là public nested interfaces, `@BusinessArea` và `@ObservedOperation` có runtime retention.
- `Object.equals/hashCode/toString` được xử lý theo **identity của proxy**; đó là một policy ví dụ, không đồng nghĩa target/proxy có cùng identity.
- `selectedCount` dùng `AtomicInteger`, phù hợp aggregate count nếu nhiều threads gọi proxy. Target tự nó vẫn có thể không thread-safe trong tình huống khác.
- `method.invoke` còn có thể ném một số lỗi khác ngoài `InvocationTargetException`, ví dụ lỗi access/argument setup; code tập trung vào exceptions từ business target.
- Code không tự enforce authorization, audit persistence, retries, transactions hoặc async completion.
- Một marker type không tự trở thành một aspect instance scope; `inScope` là Boolean captured trong factory per view, không phải task context.
- Không chứng minh mọi engine có cùng annotation inheritance hoặc pointcut syntax.

## 8. Thiết kế ranh giới theo interface vs theo package

Ở ví dụ, scope là marker trên `BillingApi`. Một dự án khác có thể giới hạn theo package hoặc module đã được kiến trúc hóa:

~~~text
# Giả mã
hasMethodMarker(ObservedOperation)
AND declaredInModule("billing-api")
AND isSupportedMethodExecution()
~~~

Nếu concern áp vào nhiều modules, có thể dùng:

~~~text
hasMethodMarker(ObservedOperation)
AND (
    implementsRole(BillingOperation)
    OR implementsRole(ShippingOperation)
)
AND NOT isTelemetryInfrastructure()
~~~

### 8.1. Khi package-based scope hợp lý

Nếu `com.acme.billing.api` là namespace được review như public boundary, có architecture tests ngăn implementation code lọt vào, scope theo package có thể có ý nghĩa bền vững. Nhưng cần:
- Quy tắc package ownership rõ.
- Test rename/move.
- Kiểm include/exclude trên subpackages.
- Không coi package matching là authorization boundary.
- Lưu ý class/module loader khi runtime introspection.

### 8.2. Khi interface-based scope hợp lý

Nếu nhiều concrete implementations thay đổi thường xuyên nhưng cùng một `BillingApi`, type role được compiler kiểm có thể ổn định hơn tên implementation package. Mark `BillingApi` thay vì từng concrete class giúp tách semantic intent khỏi công cụ xây dựng implementation.

### 8.3. Khi type marker trên implementation phù hợp

Nếu concern chỉ áp vào một số implementation dù chúng cùng interface, marker trên **implementation class** có thể phù hợp. Nhưng selection rule phải tra class của target một cách tường minh; nếu handler chỉ xét `view.isAnnotationPresent`, annotation trên implementation không được tự nhìn thấy.

Không có “annotation ở mọi nơi đều tự merge”; cần chọn **source of truth**.

### 8.4. Không suy equality của các scopes

Một object có thể implements `BillingApi` nhưng được dùng bên ngoài workflow billing. Type scope dựa interface sẽ vẫn chọn nó trong mọi proxy call tương ứng. Nếu policy thực sự phụ thuộc **dynamic context**, thêm một điều kiện context có vòng đời rõ, không hi vọng type annotation tự phản ánh caller đang chạy trong flow nào.

## 9. Phân biệt static scope và runtime context scope

**Static scope** thường là điều kiện về khai báo/type/module/package, có thể được quyết định khi build/load hoặc proxy creation. **Dynamic scope** dựa vào execution context như active business flow, principal đã xác thực hoặc trạng thái runtime.

Ví dụ:

~~~text
# Giả mã
methodMarked(ObservedOperation)
AND inOwnedBillingModule()
AND inControlFlow(CheckoutOperation)
~~~

Ở đây:
1. Marker biểu thị một operation có thể được quan sát.
2. Module/type scope chặn những code không thuộc billing.
3. Dynamic flow predicate cho phép chỉ những lần gọi nằm trong checkout scope.

### 9.1. Dynamic scope có thể vượt thread boundaries?

Một synchronous control-flow scope dựa call stack không nhất thiết tự sống qua `CompletableFuture` và executor. Nếu một logical request tiếp tục ở thread khác, phải có explicit context propagation hoặc tính năng của engine được thiết kế cho async.

### 9.2. ThreadLocal không đồng nghĩa logical request

Nếu dùng `ThreadLocal` lưu current flow, cần restore/remove bằng `try/finally`; thread pool có thể reuse thread cho request khác. Với nested contexts, chỉ `remove()` ở inner scope có thể làm mất outer context; dùng stack hoặc lưu previous value. Với async tasks, capture immutable snapshot và restore ở worker.

### 9.3. Dynamic runtime predicate có race không?

Nếu policy phụ thuộc mutable shared state như `currentTenant` trong singleton aspect, hai requests có thể ghi đè. Dữ liệu từng invocation phải đúng scope; bảo mật/authorization cần trusted source. Đừng đưa input chưa xác thực vào context rồi coi nó là bằng chứng quyền.

### 9.4. Đo đúng thời điểm

Một around advice chọn method `submitAsync` có thể return `CompletionStage` ngay. Điều kiện match quyết định một **method invocation**, không tự nghĩa completion của stage đã thành công. Nếu concern cần đo task outcome, phải hook completion theo hợp đồng và xử lý cancellation/error/ownership phù hợp.

## 10. Join-point kind: cần giới hạn cả loại điểm được can thiệp

Marker và scope không đủ nếu chúng chọn sai **loại join point**.

Một số mô hình AOP phân biệt:
- **Call:** điểm mã gọi method.
- **Execution:** điểm method body thực hiện.
- **Constructor/initialization:** theo khả năng engine.
- **Field get/set:** theo khả năng engine.
- **Handler/exception-related:** theo khả năng engine.

Nếu marker ở method, nó được dùng để chọn **call** hay **execution**? Một engine có thể nhìn interface method tại proxy call; một weaver có thể nhìn implementation execution; một reflection-based interception có metadata khác.

### 10.1. Bao gồm root hay descendants?

Nếu marker chỉ ở root `checkout()`, chọn call/execution của root khác chọn mọi methods trong dynamic flow bên dưới root. Điều kiện `within checkout cflow` cần support riêng, không phải annotation `@ObservedOperation` tự làm mọi descendants mang marker.

### 10.2. Không match ngoài supported set

Nếu một selector trông đúng nhưng engine không intercept `private`, `static`, `final`, constructors hoặc direct field reads theo mô hình hiện tại, advice không thể tự chạy ở đó. Thay đổi marker không biến một join point không được hỗ trợ thành được hỗ trợ.

### 10.3. Proxy và weaving có khả năng khác

JDK interface proxy chỉ xử lý method invocations thông qua proxy interface; source/bytecode weaving có thể thấy những join-point kinds khác tùy engine. Phải dựa vào artifact cuối và documentation của công cụ, không suy từ một demo proxy cho mọi weaving strategy.

## 11. Tránh scope quá rộng: quan sát thôi cũng có chi phí

Một marker trên method nội bộ có thể được copy-paste lên mọi helpers. Nếu scope được khai là cả module, advice có thể được chạy dày đặc:
- Metrics tăng cardinality.
- Logger ghi trùng event hoặc lộ dữ liệu đầu vào.
- Stack traces thêm nhiều frames.
- Trace span tree trở nên nhiễu.
- System I/O và allocation tăng.
- Bất cứ instrumentation error nào có thể ảnh hưởng target nếu không được cô lập.

### 11.1. Positive selection cần theo đơn vị đo

Nếu muốn đếm **logical business operations**, marker nên ở public use-case boundary. Nếu muốn đếm **storage attempts**, marker nên ở attempt boundary. Đừng đặt cả hai cùng một event name mà không phân biệt.

### 11.2. Negative exclusions nên là hợp đồng

Ví dụ một module telemetry có method `exportMetrics()` có marker do dùng chung helper annotations, nhưng aspect logging `exportMetrics()` lại gọi chính exporter có thể recursion. Exclude module/type infrastructure một cách tường minh, hoặc đặt marker semantics đủ hẹp để instrumentation methods không là candidates.

### 11.3. Exclusion theo tên helper có thể giòn

`NOT *Helper` dựa naming convention có thể bỏ nhầm một business operation. Tốt hơn là có interface/tag `InstrumentationInfrastructure` hoặc thuộc tính module ownership được công bố. Nếu vẫn dùng tên/package, quản trị refactor và có negative tests.

## 12. Marker không thay được authorization/security invariant

Một điểm đặc biệt quan trọng: **annotation chỉ là metadata**, không phải chứng chỉ quyền.

Giả sử `@ProtectedOperation` đánh dấu method cần quyền. Nếu implementation có một đường gọi trực tiếp bypass proxy, rule `marker && scope` không bao phủ đường đó. Nếu một developer quên marker, policy có thể không chạy. Nếu dữ liệu tenant nằm trong request header không xác thực, marker không biến giá trị ấy thành trusted principal.

### 12.1. Thiết kế fail-safe boundary

- Đặt security invariant tại trusted entrypoint/domain boundary phù hợp.
- Quản trị tập protected operations bằng static analysis/contract tests.
- Khi một operation mới được thêm, phải xác nhận authorization policy.
- Verify direct-target/internal calls không làm bypass invariant.
- Nếu advice có thể short-circuit, kiểm ordering với cache/fallback.
- Không coi package name hay `@ProtectedOperation` đơn lẻ là proof of authorization.

### 12.2. Scope không đồng nghĩa tenant isolation

Một type `BillingApi` có thể phục vụ nhiều tenants. Type-level scope không biết mỗi caller thuộc tenant nào. Tenant/principal context phải được xác thực và propagate đúng, không lưu vào shared singleton field rồi áp cho mọi invocations.

### 12.3. Audit bắt buộc khác debug logging

Nếu audit là regulatory/business contract phải bền vững, chỉ “advice ghi log khi marker match” chưa đủ. Cần xác định event ghi ở **trước hay sau commit**, durability, error handling, idempotency và outbox/transaction boundary. Pointcut chỉ giúp chọn điểm can thiệp; nó không tự định nghĩa độ tin cậy của event.

## 13. Khi nhiều aspects dùng chung marker và scope

Giả sử `@ObservedOperation` được Trace và Metrics cùng đọc. Đây thường là reuse hợp lý. Nhưng nếu thêm Retry, Cache, Audit và Authorization vào cùng matcher, side effects có thể đổi mạnh.

Một chuỗi advice:

~~~text
Auth(
  Trace(
    Metrics(
      Retry(
        Target
      )
    )
  )
)
~~~

chỉ là sơ đồ minh họa: thứ tự thực do công cụ/hợp đồng quyết định. Với một marker `@Observed` cho mọi tầng, việc thêm một annotation có thể vô tình bật retry cho operation không idempotent.

### 13.1. Tách vocabulary quan sát với biến đổi

- Observer: tracing/metrics/logging; lý tưởng bảo toàn result/error.
- Transformer: cache/fallback/argument normalization; thay outcomes theo policy.
- Executor-control: retry/timeout; có thể tăng số attempts hoặc đổi timing.
- Enforcer: authorization/validation; có thể reject inputs.

Các loại này không nên dùng một marker “bật tất cả” mà thiếu phân biệt eligibility.

### 13.2. Ordering và short-circuit

Với `Auth(Cache(Target))`, authorization có thể chạy trước cache. Với `Cache(Auth(Target))`, một cache hit có thể skip inner authorization. Do đó `MARKED && IN_SCOPE` không đảm bảo **thứ tự** hoặc **độ phủ khi advice short-circuit**.

### 13.3. Return/throw contracts

Một logging advice ở marker/scope đúng nhưng tự throw `IOException` từ log sink hoặc swallow target exception vẫn vi phạm hợp đồng quan sát. Scope đúng chỉ giải quyết **chọn đâu**, không giải quyết **làm gì**.

### 13.4. Logical vs attempt metrics

`Metrics(Retry(Target))` có thể đếm một logical call; `Retry(Metrics(Target))` có thể đếm mỗi attempt. Trong bài demo, `SELECTED=2` là số lần **intercepted marked-in-scope invocations** bao gồm một lỗi, không phải số success.

## 14. Refactor và evolution của annotations/scopes

Giới hạn phạm vi có thể được định nghĩa bằng interface hoặc annotation thay vì tên package, nhưng **contract vẫn tiến hóa**.

### 14.1. Khi đổi tên/move implementation

Nếu scope được neo vào `BillingApi`, một implementation mới vẫn được chọn khi caller dùng đúng interface proxy. Đây là ưu điểm của semantic boundary. Nhưng một implementation method mới không nằm trong interface sẽ không tự được proxy expose.

### 14.2. Khi chuyển marker

Chuyển `@ObservedOperation` từ interface method sang implementation method có thể làm selector cũ mất match. Một migration cần thay metadata-resolution policy hoặc giữ annotation tương thích trong quá trình chuyển.

### 14.3. Khi đổi interface scope

Bỏ `@BusinessArea` khỏi `BillingApi` khiến mọi method marker trong interface này không còn được chọn ở ví dụ. Đây là thay đổi hành vi của instrumentation, không phải một refactor thuần type name.

### 14.4. Khi thêm implementation mới

Một contract test nên tạo implementation thứ hai cùng interface view, xác nhận các method marker vẫn được quan sát. Đổi package/name của implementation không nên vô tình làm mất matched set nếu scope không dựa tên class.

### 14.5. Khi thêm overload

Nếu thêm `charge(ChargeRequest)` mà quên marker, nó không được chọn, dù cùng tên. Điều này có thể đúng hoặc sai tùy contract. Nếu thay pointcut bằng name wildcard để “tự bắt mọi overload”, rủi ro overmatching tăng. Hãy đặt marker trên đúng declaration và test.

### 14.6. Với generic erasure, bridge/synthetic methods

Generics/covariant overrides có thể tạo bridge methods khi biên dịch. Một runtime matcher trên implementation `getDeclaredMethods()` có thể thấy synthetic/bridge methods; một weaver có thể match chúng theo quy tắc khác. Không mặc định một source method tương ứng một join point trong mọi toolchain. Test với generic interface implementations nếu metrics phải không trùng lặp.

## 15. Cấu trúc pointcut tái sử dụng được

Một thiết kế bền vững thường tách **vocabulary** khỏi **implementation pattern**.

### 15.1. Định nghĩa terms

Ví dụ:
- `BusinessBoundary`: API boundary do domain team công bố.
- `ObservedOperation`: operation cần đo.
- `ApprovedModule`: module được công cụ cho phép instrument.
- `InfrastructureExclusion`: telemetry exporter, framework helper và setup paths cần loại.

### 15.2. Biểu diễn thành selector chung

~~~text
# Giả mã
NamedPointcut ObservedBusinessOperation =
    hasMethodMarker(ObservedOperation)
    AND hasTypeRole(BusinessBoundary)
    AND withinApprovedModule()
    AND isSupportedMethodJoinPoint()
    AND NOT isInstrumentationInfrastructure()
~~~

Nhiều advices quan sát có thể reuse named selector, nhưng vẫn phải có **policy riêng** cho logging fields, sampling, counters và lifecycle.

### 15.3. Một named pointcut không chữa được predicate sai

Nếu `withinApprovedModule` hiện thực bằng wildcard `*.impl.*` sai về mặt kiến trúc, đưa nó vào named pointcut chỉ tập trung hóa sự giòn. Dùng architecture tests và owner review để đảm bảo phần “scope” diễn đạt đúng nghĩa.

### 15.4. Quyền sở hữu

Đội domain xác định operation nào là boundary; đội observability có thể quản lý marker semantics; đội platform quản lý engine/toolchain; đội security quản lý trusted authorization. Những owners cần thống nhất **thay đổi marker và scope sẽ tác động tới hệ nào**.

## 16. Thứ tự ưu tiên khi áp dụng giới hạn phạm vi

Một procedure có thể dùng khi review:

1. **Xác định concern:** chọn metrics, audit, retry hay security?
2. **Xác định logical unit:** request, operation, attempt, internal call, async completion?
3. **Chọn marker:** method/type annotation hay explicit role?
4. **Chọn phạm vi:** stable interface, module, package được enforce hoặc dynamic scope.
5. **Chọn join-point kind:** call/execution/other, theo engine hỗ trợ.
6. **Chọn exclusions:** helpers, health, telemetry sinks, nested/recursive calls.
7. **Định nghĩa lookup:** interface method vs implementation, annotation inheritance.
8. **Định nghĩa lifecycle:** advice scope, task scope, context cleanup.
9. **Định nghĩa order:** cache/retry/auth/metric nesting.
10. **Định nghĩa failure policy:** target error transparency, instrumentation failures.
11. **Test positive & negative coverage** trên artifact thực.
12. **Quan sát drift** khi refactor/upgrade.

Đừng đảo thứ tự: bắt đầu bằng một wildcard “match hết” rồi mới suy ra nó đại diện business contract nào thường dẫn đến scope không rõ.

## 17. Ma trận test bắt buộc cho marker kết hợp scope

| Test case | Marker | Scope | Kỳ vọng |
| --- | --- | --- | --- |
| Business operation chuẩn | Có | Có | Được chọn; advice chạy qua đường intercept đúng. |
| Health check trong business interface | Không | Có | Không chọn. |
| Internal helper có annotation | Có | Không | Không chọn. |
| External class không annotation | Không | Không | Không chọn. |
| Thêm implementation cùng interface | Có trên interface method | Có trên interface | Giữ selection khi qua đúng proxy/view. |
| Annotation chỉ nằm trên implementation | Có ở implementation | Tùy | Theo metadata lookup rule đã công bố. |
| Rename package implementation | Không thay marker | Không thay scope semantic | Không đổi intended matches. |
| Chuyển type sang module khác | Có | Thay đổi | Được review, không silently overmatch. |
| Thêm overload chưa marked | Không ở overload | Có | Không chọn trừ policy khác. |
| Generic bridge method | Tùy metadata | Có | Không double count ngoài contract. |
| Direct target call | Có metadata | Có | Xác minh có/không intercept theo mechanism. |
| Self-invocation | Có metadata | Có | Không giả định handler thấy nếu không qua proxy. |
| Around advice skip | Có | Có | Scope/side effects và inner policy đúng. |
| Target throw | Có | Có | Lỗi không bị nuốt; cleanup đúng. |
| Async future fail | Có | Có | Phân biệt method-return và task-completion. |
| Retry nhiều attempts | Có | Có | Metric đo đúng đơn vị attempt/logical call. |
| Nested flow | Có | Có, theo flow | Không trộn context của parent/child. |
| Reused worker thread | Có | Theo task | Context cũ không rò sang request mới. |
| Multiple aspects | Có | Có | Ordering/side effects đúng. |
| Untrusted caller giả marker-like data | Không liên quan Java declaration | Tùy | Không trở thành authorization. |

### 17.1. Positive tests không đủ

Nếu chỉ assert `billing.charge("A")` có log, matcher `selected = inScope || marked` cũng có thể pass positive test. Phải assert cả hai negative cases:
- Có scope nhưng không marker.
- Có marker nhưng không scope.

Điều này phát hiện lỗi **OR thay vì AND** rất hiệu quả.

### 17.2. Error path là một phần của selection contract

Method lỗi vẫn là một selected invocation nếu marker/scope đúng. Nếu counter định nghĩa “attempts”, nó vẫn phải đếm failure. Nếu counter định nghĩa “success”, chỉ đếm bình thường sau target completion. Không nhập nhằng hai semantics.

### 17.3. Test metadata đúng nơi

Dùng reflection/JDK proxy để xác minh interface method annotation khi đó là rule. Nếu engine match implementation method, cần fixture annotation chỉ trên interface, chỉ trên target và trên cả hai để so.

### 17.4. Test scope thực thi

Một selector Boolean tính true không tự chứng minh advice chạy. Integration tests phải gọi qua proxy hoặc woven artifact thực. Nếu security policy bắt buộc, assert cả đường direct target/internals theo trust boundary.

### 17.5. Test tương tác advice chain

Thử một cache hit skip inner advice, một retry ném rồi thành công và một observer throws. Xác minh policy quan trọng không bị bỏ qua, side effects không bị lặp ngoài ý định, exceptions được bảo toàn.

## 18. Performance và chi phí metadata selection

### 18.1. Không thể kết luận annotation matcher luôn chậm

Một số engines resolve pointcut ở compile/load time; một proxy interceptor khác đọc `Method.isAnnotationPresent` tại mỗi invocation; một implementation có thể cache method metadata. Overhead phụ thuộc engine, runtime, số join points, advice body, contention và allocation.

### 18.2. Càng narrow scope, càng giảm noise

Một positive interface scope giới hạn candidates trước khi gọi advice. Điều này có thể giảm log/metric event volume, recursion và cardinality. Nhưng chỉ tối ưu khi **đúng contract**; không cắt bớt operations bắt buộc để làm biểu đồ nhẹ hơn.

### 18.3. Cache annotation metadata khi cần

Nếu custom interceptor đọc annotation nhiều lần, có thể cache `Method -> Descriptor` hoặc thông tin theo interface view. Nhưng lưu `Method/Class` keys trong global strong-reference cache có thể giữ classloaders lâu hơn khi plugin unload. Cần owner/eviction và tránh cache key quá rộng.

### 18.4. Lỗi chính thường ở advice body, không ở predicate

Một advice ghi JSON của toàn bộ payload có thể đắt hơn một annotation lookup. `@ObservedOperation` không nên mặc định nghĩa “log toàn bộ parameters/results”. Áp allowlist fields, sampling, bounded queue và dữ liệu được sanitize.

### 18.5. Metric đo đúng đơn vị

Một method được marker trong scope có thể chạy nhiều lần do retry. Nếu metric tag `logical_operations` nhưng đặt advice bên trong retry, số đếm sai dù selector hoàn toàn đúng. Test unit of measure và advice order.

## 19. Kịch bản thất bại và cách chẩn đoán

### Tình huống A — Marker có mà advice không chạy

Kiểm tra lần lượt:
1. Marker nằm ở interface/implementation đúng lookup site chưa?
2. `@Retention` có phù hợp pha xử lý metadata không?
3. Type/module/package có thuộc scope được allow không?
4. `AND/OR/NOT` có grouping sai không?
5. Join-point kind có được hỗ trợ không?
6. Class/proxy có thật sự được instrument không?
7. Caller có đi trực tiếp target hoặc self-call không?
8. Engine runtime có nạp đúng artifact/configuration không?

### Tình huống B — Advice chạy quá rộng

- Có dùng `OR` thay `AND`?
- Scope quá rộng như `all public methods`?
- Marker type-level có implicit áp mọi methods theo công cụ?
- Một meta-annotation/composed rule match cả methods ngoài ý định?
- Một target implements nhiều interfaces/roles?
- Có nhầm interface `@BusinessArea` với runtime flow điều kiện?
- Exclusion của telemetry/exporter bị thiếu?
- Generated/bridge methods đang double count?

### Tình huống C — Advice hoạt động nhưng nghiệp vụ sai

- Around advice có return/rethrow đúng contract không?
- Log/metrics sink có thể ném exception che lỗi target?
- Retry/cache/fallback được bật bởi marker không phù hợp?
- Nested precedence có làm authorization bị skip?
- Shared context có bị race giữa threads?
- Async completion có bị ghi nhận ở method-return?
- Một policy dựa input tenant chưa xác thực?

Những câu hỏi này phân biệt lỗi **selection**, lỗi **interception coverage** và lỗi **advice behavior**.

## 20. Lộ trình nâng cấp một pointcut giòn

Giả sử hiện tại aspect chọn `com.example.legacy.internal.*.do*`. Muốn đổi sang marker + scope, nên đi có kiểm soát:

### Bước 1 — Inventory và phân loại

Liệt kê các methods hiện được match và tại sao chúng thuộc concern. Phân biệt public operation, helper, event handler, storage attempt. Nếu chỉ có công cụ tính match set, vẫn cần kiểm các **actual invocations**.

### Bước 2 — Xác lập semantic vocabulary

Chọn `@ObservedOperation` cho những operations có ý nghĩa đo. Chọn type/module role `BusinessArea` cho phạm vi được phép. Công bố rằng selection mới là **giao** của hai điều kiện, không phải hợp.

### Bước 3 — Đánh dấu source có review

Gắn marker tại interface method hoặc implementation method theo lookup rule được kiểm. Đánh dấu type/interface scope ở nơi có owner. Đừng tự động mass-annotate mọi method chỉ vì nó từng match wildcard cũ.

### Bước 4 — Viết golden coverage set

Xác nhận:
- Operations bắt buộc có marker và scope.
- Methods ngoài policy bị loại.
- Internal methods không được annotate chỉ để giữ log cũ.
- Trường hợp cùng name/overload/refactor vẫn được xử lý.
- Security/audit mandatory paths có enforcement riêng.

### Bước 5 — So sánh hai selectors ở môi trường test

Chỉ so **read-only match diagnostics** nếu advice gây side effects. Bật cả hai production advices có thể làm duplicate audit, retry hoặc metrics. Review khác biệt match sets theo nghiệp vụ, không chỉ đặt mục tiêu match 100% cũ.

### Bước 6 — Chuyển dần và test runtime

Chạy integration tests trên artifact thật, gồm proxy/weaving, classloaders, interface/implementation metadata, exception paths, async và concurrency. Xác nhận error contracts và ordering vẫn đúng.

### Bước 7 — Theo dõi drift và bỏ wildcard cũ

Sau deployment, theo dõi số selected calls, missing instrumentation, unexpected names, event volume và error changes. Gỡ legacy selector khi đủ bằng chứng, không giữ hai cơ chế trái nghĩa lâu dài.

## 21. Những lỗi thường gặp

**“Có annotation là đủ chọn mọi nơi.”** Không nếu cần giới hạn architecture/trust scope.

**“Nằm trong package đúng là đủ.”** Không nếu chỉ những operations marked mới có nghĩa.

**“Dùng `OR` hay `AND` khác nhau không đáng kể.”** Sai; `OR` có thể mở phạm vi rất rộng.

**“Type-level marker luôn xuất hiện trên method declaration.”** Không; placement và metadata lookup khác nhau.

**“`@Inherited` truyền interface method marker xuống mọi implementation method.”** Không.

**“Runtime reflection thấy mọi annotation dù retention SOURCE.”** Không.

**“Marker đồng nghĩa security authorization đã được thực thi.”** Không; nó chỉ là metadata về intent.

**“Pointcut matcher match tức advice chắc chắn chạy.”** Không; còn proxy/weaving coverage và call path.

**“Một proxy chọn interface method cũng sẽ đọc annotation trên implementation method.”** Không tự động.

**“Đổi package implementation không ảnh hưởng package selector.”** Có thể làm mất match nếu scope hardcode package.

**“Trong scope thì mọi overload đều nên match.”** Không nếu mỗi overload có semantics riêng và marker chỉ áp một declaration.

**“Có annotation thì có thể retry non-idempotent command.”** Không; retry eligibility cần hợp đồng riêng.

**“Annotation scope tự biểu thị per-request state.”** Không; type marker và task context là hai khái niệm.

**“ThreadLocal tự đi theo async logical operation.”** Không; cần propagation.

**“`@Observed` có nghĩa log mọi arguments.”** Không; cần data minimization/privacy policy.

**“Một positive test chứng minh predicate AND đúng.”** Không; cần hai negative cases theo từng điều kiện.

**“Logger lỗi nên biến target success thành failure.”** Chỉ nếu đó là policy được công bố; best-effort observer không nên phá business result.

**“Hai aspects cùng marker có cùng ordering.”** Không; phải khai precedence riêng.

**“Những methods generated/bridge không quan trọng.”** Có thể ảnh hưởng reflection và double count; cần test.

**“Chỉ cần sửa wildcard tên package khi migrate.”** Không; cần định nghĩa scope semantics và ownership.

## 22. Bài tập vận dụng kèm đáp án

### Câu 1 — Marker đúng, scope sai

Một `DebugApi.cleanup()` có `@ObservedOperation` nhưng không thuộc business module. Theo `MARKED && IN_SCOPE`, advice có chạy không?

**Đáp án:** Không, giả sử không có selector bổ sung. Marker là điều kiện cần nhưng chưa đủ.

### Câu 2 — Scope đúng, marker thiếu

`BillingApi.health()` thuộc `@BusinessArea` nhưng không `@ObservedOperation`. Kết quả?

**Đáp án:** Không được chọn. Đây là negative test xác nhận giới hạn không tự kéo tất cả methods vào advice.

### Câu 3 — Sửa AND thành OR

Predicate được đổi thành `MARKED || IN_SCOPE`. Có nguy cơ gì?

**Đáp án:** Mọi method trong scope hoặc mọi method marked ngoài scope có thể được chọn, gây overmatching, metric noise và side effects của policies khác.

### Câu 4 — Annotation interface

Marker nằm trên `BillingApi.charge`, target implementation override không khai annotation. Java reflection trên implementation method có tự tìm marker interface không?

**Đáp án:** Không mặc định. Trong demo handler đọc `Method` ở interface view nên thấy. Engine khác phải có lookup rule tường minh.

### Câu 5 — Thay implementation

Team đổi `OldBillingImpl` thành `NewBillingImpl`, cả hai implement `BillingApi`. Selector trong demo có đổi không?

**Đáp án:** Không nếu vẫn proxy qua cùng interface view; điều kiện dựa `BillingApi` và method annotation, không dựa concrete class.

### Câu 6 — Direct-target call

Một caller giữ `BillingTarget` gốc và gọi `charge`, không qua proxy. Marker và scope đủ chưa?

**Đáp án:** Chưa để handler chạy; đó là lỗi hoặc giới hạn **interception coverage**, không phải predicate.

### Câu 7 — Exception

`billing.charge("BAD")` có marker và scope, target throw. Nếu counter đếm selected attempts, có cộng không?

**Đáp án:** Có. Demo đếm trước invocation, `SELECTED=2` gồm success A và failure BAD.

### Câu 8 — Runtime retention

Một handler dùng `Method.isAnnotationPresent` nhưng annotation khai `@Retention(SOURCE)`. Kỳ vọng có marker ở runtime?

**Đáp án:** Không. Cần runtime retention cho reflection-based selection.

### Câu 9 — Authorization

Một method thiếu marker bảo mật và không match advice, nhưng vẫn xử lý dữ liệu nhạy cảm. Giải pháp có thể là “cho phép” mặc định không?

**Đáp án:** Không mặc định. Cần trusted security enforcement và contract/architecture tests bắt buộc coverage.

### Câu 10 — Async metric

Một selected method trả future pending. Advice after-return ghi success khi nhận future. Nó có đo task completion không?

**Đáp án:** Không; đó chỉ là successful method return-handle. Muốn đo task outcome phải hook completion.

### Câu 11 — `cflow` và type scope

Một join point có marker/type scope nhưng không nằm trong checkout flow hiện hành. Nếu selector còn `AND inFlow(checkout)`, nó có match không?

**Đáp án:** Không. Context predicate là điều kiện thứ ba phải đúng; engine cần hỗ trợ và propagation phù hợp.

### Câu 12 — Named pointcut

Trace và Metrics dùng cùng named pointcut `ObservedBusinessOperation`. Team thêm Retry dùng luôn tên ấy. Điều gì cần review?

**Đáp án:** Marker chỉ mô tả observability, không bảo đảm target idempotent. Retry cần eligibility, attempt/failure policy và ordering riêng.

### Câu 13 — Nhãn log

Một method `charge` được retry ba lần. Advice metric ở trong retry ghi ba events. Đây có phải bug?

**Đáp án:** Nếu metric là **attempts** thì có thể đúng; nếu là **logical charge operations** thì sai. Đặt selector/order theo đơn vị đo.

### Câu 14 — Metadata qua module

Marker và scope đúng, nhưng runtime không truy cập được method do module access constraint. Có nên bỏ qua warning?

**Đáp án:** Không. Matching và ability-to-intercept là hai giai đoạn; cần chỉnh toolchain/boundary hợp lệ hoặc dùng wrapper/API phù hợp.

## 23. Checklist review trước khi triển khai

1. **Intent:** Marker có diễn đạt một concern duy nhất và rõ không?
2. **Placement:** Annotation ở method/interface/class nào?
3. **Retention:** Metadata có sẵn ở pha công cụ dùng không?
4. **Target:** Annotation `@Target` có đúng nơi khai báo không?
5. **Lookup:** Tool đọc interface declaration hay implementation/target?
6. **Type scope:** Có role/API boundary được công bố không?
7. **Module scope:** Package/module có phải architecture boundary thực không?
8. **Dynamic scope:** Có phụ thuộc request/control-flow context và có propagation đúng không?
9. **Logic:** Dùng `AND`, `OR`, `NOT` với grouping tường minh?
10. **Exclusions:** Helpers/exporters/health checks được loại có chủ đích?
11. **Join point:** Call/execution/constructor/field join point nào được hỗ trợ?
12. **Proxy:** Caller có đi qua đúng proxy không?
13. **Weaving:** Binary/classloader artifact thực sự được weave chưa?
14. **Self-invocation:** Internal calls có được chọn không, theo cơ chế nào?
15. **Generics:** Overloads/bridges/synthetic methods có gây mismatch không?
16. **Concurrency:** Shared aspect state có race, visibility hoặc giữ args/results?
17. **Async:** Method return khác task completion, có lifecycle đúng?
18. **Errors:** Result/exception của target có bị masking không?
19. **Ordering:** Retry/cache/auth/metrics có precedence và semantics rõ?
20. **Security:** Trusted enforcement có thể bị bypass do thiếu marker/scope không?
21. **Positive test:** Method marked và in-scope được instrument?
22. **Negative tests:** Marked ngoài scope và unmarked trong scope đều không instrument?
23. **Refactor test:** Đổi class/package implementation không phá intended match?
24. **Migration:** Marker/type/module change có versioning và rollout?
25. **Diagnostics:** Có đo missing/extra selected invocations?
26. **Performance:** Advice overhead, metric cardinality, logging rate và p99 hợp lý?
27. **Privacy:** Không tự log secrets chỉ vì marker tồn tại?
28. **Ownership:** Ai duyệt việc thêm/bỏ marker và scope?
29. **Fallback:** Khi metadata thiếu, policy fail-safe thế nào?
30. **Rollback:** Có thể thu hẹp/disable an toàn khi overmatching phát sinh?

## Kết luận

**Kết hợp marker tường minh với giới hạn phạm vi** giúp pointcut vừa có **ý định rõ ở từng join point**, vừa có **ranh giới áp dụng được quản trị**. Công thức `MARKED && IN_SCOPE` tránh hai sai lầm phổ biến: chọn mọi thứ có cùng annotation ở bất cứ đâu, hoặc chọn mọi operation chỉ vì nó nằm cùng package/type. Khi cần, thêm ràng buộc join-point kind, exclusions và dynamic context theo ngữ nghĩa được công bố.

Tuy nhiên, một selector đúng vẫn chưa chứng minh advice thật sự chạy hoặc an toàn: phải xác minh proxy/weaving coverage, metadata lookup, direct/self calls, order của các aspects, error/result contracts, privacy và security boundary. Java 17 demo dùng ba tình huống đối chứng để chứng minh predicate AND và failure path; còn các hệ thống lớn nên bổ sung **architecture governance, positive/negative coverage tests, refactor tests và runtime integration verification**. Mục tiêu không phải thêm annotation càng nhiều càng tốt, mà là để **mỗi marker có nghĩa**, **mỗi scope có owner** và **mỗi policy chạy đúng nơi, đúng lúc, đúng hợp đồng**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="annotation-metadata-008">Kiểm tra annotation thiếu, mâu thuẫn hoặc đặt sai chỗ</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra annotation thiếu, mâu thuẫn hoặc đặt sai chỗ trong AOP

Trong **lập trình hướng khía cạnh (Aspect-Oriented Programming – AOP)**, annotation cho phép method, type hoặc tham số **khai báo ý định** liên quan đến một concern cắt ngang: quan sát, ghi audit, kiểm soát quyền, retry, timeout, cache hoặc validation. Nhưng việc có annotation trong source **không có nghĩa hành vi đã được thực thi**. Một hệ thống dùng annotation-driven AOP có thể gặp ba loại lỗi đặc biệt nguy hiểm:

1. **Thiếu:** nơi cần annotation bắt buộc lại không có, hoặc annotation có retention khiến bộ đọc metadata không tìm thấy.
2. **Mâu thuẫn:** hai annotation chỉ thị hành vi đối nghịch, giá trị thuộc tính không hợp lệ, hoặc nhiều policy không thể cùng thỏa.
3. **Đặt sai chỗ:** annotation hợp lệ theo cú pháp Java nhưng nằm trên member hoặc loại join point **không được hệ thống interception hiện tại quan sát**, hoặc được đặt ở implementation trong khi resolver chỉ tra cứu interface.

Ví dụ một method **@Observe** bị thiếu có thể dẫn tới không có metric; một method vừa có **@Observe** vừa **@IgnoreObserve** có thể được chạy tùy cách xử lý mơ hồ; một **@RequiresPermission** chỉ gắn trên method mà runtime không intercept có thể khiến developer tin sai rằng quyền truy cập đã được bảo vệ.

**Mục tiêu bài:** biết định nghĩa policy metadata trước khi kiểm tra; nhận biết lỗi cú pháp, lỗi semantic placement và lỗi interception coverage; dùng cơ chế build/startup validation; phân biệt interface annotation với implementation annotation; xác minh kết quả bằng Java 17 và một bộ test có oracle độc lập.

Bài nằm trong chủ đề **Annotation và siêu dữ liệu** và chỉ trình bày kiến thức **AOP cơ bản/nâng cao nói chung**, không dựa trên một bộ framework cụ thể. Ví dụ Java 17 là một **policy validator đọc metadata bằng reflection**; nó không phải bytecode weaver và không thay thế các test chạy advice thật.

## 1. Phân biệt annotation, pointcut và enforcement

Các khái niệm:
- **Join point** là điểm chương trình có thể can thiệp, ví dụ method call hoặc method execution nếu mô hình hỗ trợ.
- **Pointcut** là điều kiện chọn join point, có thể dựa vào method/type annotation.
- **Advice** là hành vi before, after, around hay dạng khác được thực thi tại join point đã chọn.
- **Aspect** nhóm pointcut/advice và chính sách áp dụng.
- **Weaving/interception** đưa advice vào đường chạy bằng compile-time, post-compile, load-time hoặc proxy/wrapper.
- **Annotation metadata** chỉ thể hiện declaration và attributes; không tự gọi advice.
- **Metadata resolver** quyết định annotation nào **có hiệu lực** khi các khai báo nằm trên interface, implementation, superclass, type hoặc meta-annotation.
- **Validator** kiểm tra completeness, conflicts, placement, retention và cấu hình trước hoặc trong khi ứng dụng chạy.

~~~text
Source annotation declares intention
                |
                v
       Annotation validator
        /              \
   Invalid             Valid
       |                 |
  Error/Warning   Effective metadata lookup
                         |
                         v
                 Pointcut selection
                         |
                         v
             Does interception occur?
                  /            \
                 No            Yes
                 |              |
          No advice event    Advice + target
~~~

Điều quan trọng là **không đánh đồng ba câu hỏi**: metadata có đúng không, join point có được intercept không, và business/security contract có được giữ đúng khi chạy không.

Một annotation hoàn hảo vẫn có thể vô hiệu nếu lời gọi bypass proxy. Ngược lại, một advice có thể chạy trên một method không annotate nếu pointcut lựa chọn bằng signature hoặc package. Hai điều này đều phụ thuộc vào hợp đồng AOP.

## 2. Phân loại lỗi bằng một bảng kiểm chứng

| Loại | Ví dụ | Cần kiểm gì? |
| --- | --- | --- |
| Thiếu bắt buộc | Public API operation chưa có @Observe | Interface/type nào thuộc rule, opt-out có được phép? |
| Mâu thuẫn trực tiếp | Cả @Observe và @IgnoreObserve | Mutually exclusive hay có precedence? |
| Giá trị sai | @Observe("") | Operation label có nonblank, hợp lệ? |
| Đặt sai ElementType | Annotation METHOD đặt trên field | Compiler kiểm @Target |
| Đặt sai AOP boundary | Annotation ở private helper, proxy chỉ thấy interface | Method có thực sự instrument được không? |
| Sai nguồn metadata | Interface không có marker, implementation có | Resolver đọc nơi nào? |
| Sai retention | SOURCE annotation được runtime reflection tìm | @Retention và pha đọc |
| Conflict type/method | Type yêu cầu audit, method muốn bỏ | Override có được phép? |
| Conflict giữa aspect | Hai advice có precedence cycle | Kiểm graph ưu tiên |
| Repeatable/composed | Hai role annotations hoặc meta-annotation | Resolver hiểu nhiều marker ra sao? |
| Nguy cơ động | Marker security nhưng direct call bypass | Integration test actual enforcement |

**Java compiler không tự hiểu mọi ràng buộc AOP.** Compiler bắt được annotation đặt lên loại phần tử mà @Target cấm; nhưng nó không biết một team có cấm @Observe trên private method hay không. Đây là chính sách kiến trúc phải được mô tả thêm.

## 3. Muốn tìm annotation thiếu phải xác định “bắt buộc ở đâu”

Một lỗi phổ biến là quét mọi method rồi báo thiếu annotation ở mọi getter, helper, generated method và function nội bộ. Khi đó validator tạo false positives, khiến người phát triển tắt luôn rule.

Thay vào đó, viết một **contract có thể kiểm chứng**. Ví dụ:
- Những interface đánh dấu **@CheckedService** chứa những public operations cần quyết định policy observation.
- Mỗi method được **khai báo trong interface đó** phải có **chính xác một** trong hai lựa chọn: @Observe hoặc @IgnoreObserve.
- @IgnoreObserve chỉ dành cho một concern quan sát **không bắt buộc**, và phải có reason.
- Nếu có @Observe, operation name không được rỗng.
- Cả hai annotation trên cùng method là lỗi; không chọn annotation “đứng trước” trong source.
- Implementation-only annotations không tự được đọc bởi interface-only resolver.
- Method có annotation không nằm trong interface được kiểm như một trường hợp **có thể đặt ngoài phạm vi**.

Phạm vi trên được chọn vì mục tiêu là xây validator **minh họa**. Một hệ thống thực tế có thể chọn mọi exported operation theo class, package, module, annotation khác hoặc tập static join points mà weaver nhận diện được.

### 3.1 Có phải mọi annotation đều bắt buộc?

Không. Với logging và metrics, policy có thể opt-in, chỉ annotate những method cần thiết. Nhưng nếu module tuyên bố “mọi public operation phải audit”, thì missing annotation là vi phạm của một **invariant bắt buộc**.

Với authorization, cần thận trọng: **đừng cho một opt-out dễ dàng** làm mất kiểm tra quyền. Có thể dùng fail-closed khi cấu hình thiếu/mâu thuẫn và bảo vệ tại ranh giới đáng tin thay vì phụ thuộc duy nhất vào annotation.

### 3.2 Không dùng việc thiếu log để kết luận annotation thiếu

Không có log cũng có thể do:
- Method không hề chạy.
- Method có chạy nhưng không qua proxy.
- Around advice ngoài short-circuit trước khi đến advice bên trong.
- Advice đang chạy nhưng sink/log exporter bị filter hoặc lỗi.
- Annotation ở interface, trong khi resolver lại đọc implementation.
- Exception path không đi qua after-returning advice.

Muốn chẩn đoán, tách **declaration test**, **metadata lookup test**, **interception coverage test** và **runtime behavior test**.

## 4. @Target: cú pháp đúng chưa đủ chứng minh vị trí đúng

Java định nghĩa annotation bằng **@interface**. Ví dụ:

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Observe {
    String value();
}
~~~

Đây là mảnh khai báo, không có hàm main. @Target(METHOD) quy định annotation được dùng trên **method declaration**. Nó không nói method phải public hay thuộc interface.

Một số ElementType khác: **TYPE**, **FIELD**, **PARAMETER**, **CONSTRUCTOR**, **ANNOTATION_TYPE**, **TYPE_USE**. Chọn đúng loại tùy dữ liệu pointcut/resolver có thể quan sát.

Nếu đặt annotation chỉ cho METHOD lên một field, compiler sẽ từ chối. Nhưng ví dụ sau **có thể hợp cú pháp**:

~~~java
@Observe("internal")
private void helper() {
    // helper không được expose qua interface proxy
}
~~~

Nếu project dùng **JDK interface proxy**, lời gọi tới private helper **không xuất hiện như một lời gọi method trên proxy interface**. Vì vậy annotation ở đây **đặt sai chỗ theo cơ chế AOP đã chọn**, dù không phải lỗi @Target.

Ngược lại, một bộ weaver trực tiếp lên method executions có thể có tập join point khác. Không khẳng định mọi cơ chế AOP đều không thể can thiệp private method. Validator phải được viết **theo execution model thực tế**, không theo giả định mơ hồ.

### 4.1 Type-level và method-level khác nhau

Một annotation đặt trên class không tự trở thành annotation của mọi method. Muốn type-level marker tạo default policy cho tất cả method, resolver phải có **quy tắc tường minh**:
- Bao gồm public declared methods hay cả inherited methods?
- Có loại static/bridge/synthetic methods không?
- Method-level opt-out có được phép không?
- Nếu superclass/interface và concrete type xung đột, bên nào thắng?

Tương tự, annotation trên parameter hoặc type-use không tự hiện diện trên Method.getAnnotation.

## 5. @Retention: annotation có thể biến mất khỏi nơi runtime đọc

Java hỗ trợ:
- **RetentionPolicy.SOURCE:** chỉ cần ở source; không lưu trong classfile theo chính sách retention.
- **RetentionPolicy.CLASS:** được lưu trong classfile, nhưng không có hợp đồng runtime reflection như RUNTIME.
- **RetentionPolicy.RUNTIME:** có thể đọc bằng reflection trên phần tử tương ứng khi chạy.

Nếu một runtime interceptor tra **Method.getAnnotation(Observe.class)**, annotation phải có **RUNTIME retention**. Nếu kiến trúc chỉ kiểm bằng source processor, SOURCE có thể là lựa chọn phù hợp.

Tuy nhiên, chọn RUNTIME **không tự sửa** sai khác giữa interface/implementation, không làm proxy intercept target raw calls và không bảo đảm policy không bị bypass.

Bộ kiểm tra nên xác định:
1. Annotation type có retention mong đợi không?
2. Công cụ đọc source, classfile hay runtime element?
3. Nếu runtime, đang đọc đúng Method/Type/Parameter chưa?
4. Nếu có bytecode transformation/introduction, metadata thực tế đã được giữ/biến đổi thế nào?
5. Reflection test có kiểm đúng element sau compile không?

## 6. Interface method khác implementation method

Một ví dụ kinh điển:

~~~java
interface BillingApi {
    @Observe("charge")
    String charge(String id);
}

class BillingImpl implements BillingApi {
    public String charge(String id) {
        return "charged:" + id;
    }
}
~~~

Đây là mảnh minh họa cần @Observe đã khai báo. Annotation nằm trên **interface method**. Khi gọi **BillingApi.class.getMethod(...).getAnnotation(Observe.class)**, ta có thể thấy annotation; nhưng **BillingImpl.class.getMethod(...).getAnnotation(Observe.class)** không tự chứa annotation method từ interface.

Java **@Inherited** không giải quyết điều này. @Inherited liên quan class-level annotation được kế thừa qua superclass theo API phù hợp; nó **không tự sao chép method annotations**, và không thực hiện interface-to-implementation annotation inheritance.

Nếu AOP proxy cung cấp interface Method cho interceptor, một resolver đọc interface là hợp lý. Nếu bộ weaver chọn implementation method execution, resolver lại cần tìm metadata từ implementation hoặc theo chính sách fallback sang interface. **Không có resolution order dùng chung cho mọi bộ AOP.**

### 6.1 Xung đột giữa các nguồn metadata

Giả sử:
- Interface method có @Observe("transfer").
- Implementation method có @IgnoreObserve.
- Class có một annotation mặc định quan sát.

Những nguồn đó **không tự hòa giải**. Cần quyết định:
- Một marker explicit trên implementation có được override interface contract?
- Nếu có, opt-out có được phép với concern bảo mật?
- Nếu nhiều interfaces khai cùng signature nhưng policy khác, có reject không?
- Một method override từ superclass có được “kế thừa” policy theo resolver tự viết không?
- Với overloaded methods, resolver có so đúng parameter types không?

Một phương án an toàn với invariant bắt buộc là **fail fast** khi khai báo trái nhau, thay vì dựa vào thứ tự reflection để chọn ngẫu nhiên.

## 7. Annotation conflict là một quan hệ chính sách

Hai annotation khác loại cùng đặt lên một method có thể hợp lệ cú pháp:

~~~java
@Observe("refund")
@IgnoreObserve(reason = "manual")
String refund();
~~~

Đây là mảnh minh họa. Nếu policy quy định một cái phải observe và cái kia phải skip, ta có một **semantic conflict**.

Nhưng **không phải mọi cặp annotation đều xung đột**. @Observed và @Measured có thể là hai concern bổ sung. Validator cần có **compatibility matrix**:

| Cặp/quan hệ | Có thể hợp lệ? | Câu hỏi cần trả lời |
| --- | --- | --- |
| Observe + IgnoreObserve | Không nếu mutually exclusive | Từ chối hay có override hợp lệ? |
| Observe + Measured | Có | Thứ tự advice/đơn vị đếm là gì? |
| Retry + Transaction | Có điều kiện | Mỗi attempt có transaction riêng không? |
| Retry + non-idempotent mutation | Rủi ro | Dedup/idempotency và budget? |
| Cache + Authorization | Có điều kiện | Authorization có trước private cache hit? |
| Type policy + method policy | Có điều kiện | Rule override có được phép? |
| Interface policy + impl policy | Có điều kiện | Nguồn metadata nào ưu tiên? |
| Hai precedence edges ngược nhau | Không nếu cả hai bắt buộc | Có cycle không? |

Một conflict checker có thể cần kết hợp:
- Kiểm cặp mutually exclusive.
- Kiểm attribute values/uniqueness.
- Kiểm graph precedence của nhiều aspects.
- Kiểm security restrictions và những combination bị cấm.

**Không dùng thứ tự annotation trong source làm thứ tự advice.** Thứ tự advice do precedence/weaving model quyết định, và cần test success, error, short-circuit, retry.

## 8. Ví dụ Java 17 hoàn chỉnh: validator thiếu, xung đột, sai chỗ

Chương trình **AnnotationRuleDemo.java** dùng Java reflection và policy riêng. Nó có một interface hợp lệ và một interface có lỗi:
- **ARC001:** method trong contract thiếu cả Observe lẫn IgnoreObserve.
- **ARC002:** hai annotation mutually exclusive cùng tồn tại.
- **ARC003:** Observe có operation rỗng.
- **ARC004:** annotation trên implementation method **không thuộc interface** đang được validate.
- **ARC005:** annotation chỉ trên implementation không thấy được với **interface-only metadata resolver**.

ARC001–ARC004 là **ERROR** theo policy ví dụ; ARC005 là **WARNING** để yêu cầu sửa/kiểm tra. Với security hoặc audit bắt buộc, một implementation-only annotation không có hiệu lực cũng có thể cần được nâng thành ERROR.

Mã được **biên dịch và thực thi bằng JDK với --release 17**, không phụ thuộc thư viện ngoài. Lưu thành **AnnotationRuleDemo.java**:

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class AnnotationRuleDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface CheckedService {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Observe { String value(); }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface IgnoreObserve { String reason(); }

    @CheckedService
    interface HealthyApi {
        @Observe("ping") String ping();
        @IgnoreObserve(reason = "low value") String health();
    }

    static final class HealthyImpl implements HealthyApi {
        public String ping() { return "pong"; }
        public String health() { return "ok"; }
    }

    @CheckedService
    interface BillingApi {
        @Observe("charge") String charge(String id);
        String status();
        @Observe("refund") @IgnoreObserve(reason = "manual") String refund();
        @Observe("") String report();
        @IgnoreObserve(reason = "low value") String health();
    }

    static final class BillingImpl implements BillingApi {
        public String charge(String id) { return "charged:" + id; }
        @Observe("status") public String status() { return "ready"; }
        public String refund() { return "refunded"; }
        public String report() { return "report"; }
        public String health() { return "ok"; }
        @Observe("internal") private String helper() { return "secret"; }
    }

    static List<String> validate(Class<?> api, Class<?> implementation) {
        List<String> issues = new ArrayList<>();
        if (!api.isAnnotationPresent(CheckedService.class)) {
            issues.add("ERROR ARC000 " + api.getSimpleName()
                    + ": missing @CheckedService");
        }

        Method[] interfaceMethods = api.getDeclaredMethods();
        Arrays.sort(interfaceMethods, Comparator.comparing(Method::getName));
        for (Method method : interfaceMethods) {
            Observe observed = method.getAnnotation(Observe.class);
            IgnoreObserve ignored =
                    method.getAnnotation(IgnoreObserve.class);
            if (observed == null && ignored == null) {
                issues.add("ERROR ARC001 " + method.getName()
                        + ": missing @Observe or @IgnoreObserve");
            } else if (observed != null && ignored != null) {
                issues.add("ERROR ARC002 " + method.getName()
                        + ": conflicting @Observe and @IgnoreObserve");
            } else if (observed != null && observed.value().isBlank()) {
                issues.add("ERROR ARC003 " + method.getName()
                        + ": blank operation");
            }
        }

        for (Method implMethod : implementation.getDeclaredMethods()) {
            Observe mark = implMethod.getAnnotation(Observe.class);
            if (mark == null) continue;
            try {
                Method contract = api.getMethod(
                        implMethod.getName(), implMethod.getParameterTypes());
                if (!contract.isAnnotationPresent(Observe.class)) {
                    issues.add("WARNING ARC005 " + implMethod.getName()
                            + ": implementation-only @Observe"
                            + " invisible to interface-only resolver");
                }
            } catch (NoSuchMethodException error) {
                issues.add("ERROR ARC004 " + implMethod.getName()
                        + ": @Observe on method absent from interface");
            }
        }
        issues.sort(Comparator.naturalOrder());
        return issues;
    }

    static void scenario(String name, Class<?> api, Class<?> implementation) {
        List<String> issues = validate(api, implementation);
        System.out.println("CASE " + name);
        issues.forEach(System.out::println);
        long errors = issues.stream()
                .filter(s -> s.startsWith("ERROR")).count();
        long warnings = issues.stream()
                .filter(s -> s.startsWith("WARNING")).count();
        System.out.println("RESULT " + (errors == 0 ? "PASS" : "FAIL")
                + " errors=" + errors + " warnings=" + warnings);
    }

    public static void main(String[] args) {
        scenario("valid", HealthyApi.class, HealthyImpl.class);
        scenario("invalid", BillingApi.class, BillingImpl.class);
    }
}
~~~

Biên dịch/chạy bằng **JDK 17+**:

~~~bash
javac --release 17 AnnotationRuleDemo.java
java AnnotationRuleDemo
~~~

Stdout đã được kiểm chứng:

~~~text
CASE valid
RESULT PASS errors=0 warnings=0
CASE invalid
ERROR ARC001 status: missing @Observe or @IgnoreObserve
ERROR ARC002 refund: conflicting @Observe and @IgnoreObserve
ERROR ARC003 report: blank operation
ERROR ARC004 helper: @Observe on method absent from interface
WARNING ARC005 status: implementation-only @Observe invisible to interface-only resolver
RESULT FAIL errors=4 warnings=1
~~~

### 8.1 Vì sao HealthyApi PASS?

**ping()** có @Observe, **health()** có @IgnoreObserve với reason. Cả hai phù hợp rule phải chọn một trong hai. Implementation không có annotation không hiệu lực bị kiểm tra. Kết quả là **0 errors, 0 warnings**.

Nếu một concern **bắt buộc**, không nên lấy opt-out ví dụ làm chính sách cho phép bỏ kiểm tra quyền. Đây chỉ là mô hình cho observability có quy định bỏ qua rõ.

### 8.2 ARC001: status thiếu trên interface

**BillingApi.status()** không khai báo lựa chọn nào, nên vi phạm contract được @CheckedService đánh dấu. Có @Observe trên **BillingImpl.status()** cũng **không sửa** sự thiếu ở interface nếu resolver chỉ đọc interface method.

Điều này giải thích vì sao cùng status có cả ARC001 và ARC005: đó là hai bằng chứng khác nhau của **thiếu contract annotation** và **implementation-only intent không hiển thị**.

### 8.3 ARC002: refund mâu thuẫn

**BillingApi.refund()** vừa @Observe vừa @IgnoreObserve. Hai marker đều hợp lệ với Java METHOD target, nhưng mâu thuẫn theo policy ứng dụng. Không được chọn một cách tình cờ annotation nằm trước trong source.

### 8.4 ARC003: report có annotation nhưng cấu hình sai

**BillingApi.report()** có @Observe("") nên không bị thiếu marker, song operation label không hợp lệ. Kiểm giá trị khác kiểm sự hiện diện. Trong thực tế, còn cần giới hạn độ dài label, định dạng, cardinality và trường chứa dữ liệu nhạy cảm.

### 8.5 ARC004: helper được đánh dấu ngoài phạm vi

**BillingImpl.helper()** là private và không có method tương ứng trên interface. Trong ví dụ **interface-only** đó là sai vị trí. Với execution weaving có khả năng nhìn các method khác, rule có thể cần thay đổi; ARC004 là luật của **mô hình kiểm cụ thể**, không phải điều cấm của Java.

### 8.6 ARC005: implementation-only marker

Implementation method status có @Observe, nhưng interface method không có. Một interceptor chỉ đọc **Method từ interface proxy** sẽ không thấy marker implementation. Warning cần dẫn người phát triển tới annotation placement policy hoặc metadata resolver hỗ trợ hợp đồng khác.

### 8.7 Phạm vi mà ví dụ chưa chứng minh

Đây là **validator metadata bằng reflection**, không tạo proxy và không weave method execution:
- Nó chỉ kiểm **các method được khai báo trong interface cụ thể**, không quét toàn ứng dụng.
- Nó chưa kiểm inherited interface methods hoặc generic bridge methods.
- Nó chưa kiểm reason rỗng của @IgnoreObserve; có thể bổ sung khi policy yêu cầu.
- Nó chưa chứng minh aspect được đăng ký/pointcut được áp dụng runtime.
- Nó không kiểm security principal, token hoặc tenant identity.

**PASS từ validator không phải bằng chứng đã thực thi advice.** Muốn chứng minh behavior, tạo integration test gọi method qua boundary được instrument và assert hit count, return value, exception, short-circuit và đường bypass.

## 9. Kiểm tra ở compile-time, startup và runtime

Có ba nhóm kiểm tra bổ sung:

| Pha | Kiểm gì tốt? | Không tự chứng minh |
| --- | --- | --- |
| **Compile-time/source** | @Target, @Retention policy, types, attributes, contract completeness qua processor/analyzer | Target runtime được chọn chính xác |
| **Post-compile/bytecode** | Classfile annotations, generated methods, introductions, cấu trúc sau weaving | Mọi request được phân quyền |
| **Startup/reflection** | Effective runtime metadata, conflict interface/implementation, configuration | Mọi lời gọi qua proxy |
| **Runtime integration** | Advice có chạy đúng khi join point xảy ra, result/error/cancellation | Cấu trúc toàn bộ artifact nếu coverage test chưa đủ |

Một annotation processor có thể kiểm một số annotation declarations ở compile-time và phát warning/error. Nhưng processor **không tự chuyển mọi method body thành around advice**. Runtime weaving hoặc proxy là một trách nhiệm riêng.

### 9.1 Fail fast theo mức nghiêm trọng

Với required metadata và vi phạm chắc chắn, nên phát **ERROR** ở build hoặc khởi động. Với một dấu hiệu có thể vẫn hợp lệ theo model, phát **WARNING** kèm scope rõ ràng.

Không âm thầm bỏ qua lỗi vì hệ thống “vẫn chạy” trong môi trường thử nghiệm. Nếu validator bắt buộc bị tắt, trạng thái đúng là **chưa được kiểm tra**, không phải PASS.

### 9.2 Diagnostic có thể hành động

Một thông báo tốt có:
- ID ổn định và severity.
- Interface/type/method signature.
- Annotation thấy được và annotation được yêu cầu.
- Ranh giới instrumentation/resolution.
- Hướng sửa hoặc quy trình xin ngoại lệ.

Ví dụ:

~~~text
ERROR ARC001 BillingApi.status(): no observation choice
Observed: no @Observe and no @IgnoreObserve on interface method
Policy: @CheckedService requires exactly one choice per method
Suggestion: annotate interface method or change policy explicitly
~~~

Đây là **mẫu diagnostic giải thích**, không phải stdout thực tế của demo.

## 10. @Inherited, @Repeatable, meta-annotation và override

### 10.1 @Inherited không áp lên method overrides như nhiều người tưởng

Java @Inherited chủ yếu liên quan class-level annotations được truy vấn qua superclass hierarchy. Nó không khiến annotation method ở interface tự xuất hiện trên implementation method.

Nếu muốn một annotation được coi là kế thừa ở method, cần **metadata resolver riêng**, test superclass, interface, override và xung đột.

### 10.2 @Repeatable

Khi annotation type có @Repeatable và container phù hợp, một element có thể chứa nhiều declarations cùng loại. Một resolver muốn lấy tất cả thường dùng **getAnnotationsByType**.

Không được mặc định repeatable annotation nghĩa advice tự chạy nhiều lần. Hợp đồng có thể là một tập policy/labels, còn advice chỉ chạy một lần.

Các điều cần kiểm:
- Duplicate labels có hợp lệ không?
- Hai permissions kết hợp AND hay OR?
- Nếu repeatable xung đột với type-level default, precedence ra sao?
- Code có đang đọc getAnnotation rồi bỏ sót phần tử trong container không?

### 10.3 Meta-annotation

Một annotation type có thể được trang trí bằng annotation khác; Java reflection **không tự** chuyển mọi meta-annotation thành direct annotation trên method sử dụng nó.

Nếu hỗ trợ composed annotations, resolver phải duyệt đồ thị meta-annotation, chống recursion/cycle, xử lý attribute aliases, trùng metadata và precedence. Không xây resolver theo giả định “có annotation ở đâu đó trong graph thì như đặt trực tiếp”.

## 11. Nhiều aspect và policy xung đột

Method có thể đồng thời được quan sát, ghi audit và đo thời gian mà **không hề mâu thuẫn**. Vấn đề là **ngữ nghĩa kết hợp**:
- Advice nào bao ngoài advice nào?
- Nếu cache short-circuit, metric/audit bên trong có được gọi không?
- Nếu Retry gọi continuation nhiều lần, ta đếm request hay attempts?
- Nếu security advice được đặt bên trong một cache trả private data, có bỏ qua authorization bắt buộc không?
- Nếu một advice dịch ngoại lệ, audit có thấy kết quả cuối hay exception gốc?
- Nếu một method trả CompletionStage, success event được ghi khi trả stage hay khi stage hoàn tất?

Một graph precedence có cạnh A→B, B→C, C→A là **bất khả thi** nếu tất cả cạnh đều có nghĩa bắt buộc A bao ngoài B. Validator architecture có thể phát hiện cycle trước khi chạy.

Nhưng **thứ tự annotation xuất hiện ở source không đồng nghĩa thứ tự advice**; chỉ dùng policy công bố và kiểm thử execution order.

## 12. Annotation đúng nhưng không được intercept

Với proxy-based AOP:
- Gọi qua proxy có thể kích hoạt handler/advice.
- Gọi raw target có thể **bypass proxy**.
- Trong delegation model, self-invocation trên target có thể không đi qua proxy.
- Method không xuất hiện trên proxy interface không thể được dispatch như interface method tương ứng.
- Các cơ chế weaving trực tiếp có tập join point khác.

Vì vậy **wrong placement** không chỉ là @Target sai mà còn là **annotation đặt trên một điểm không thể được cơ chế áp dụng thấy**.

### 12.1 Cách phân tích khi advice không chạy

~~~text
Did the method actually execute?
       |
       +-- No --> no join point occurrence, no advice expected
       |
       +-- Yes --> did the call cross an interception boundary?
                       |
                       +-- No --> coverage/bypass issue
                       |
                       +-- Yes --> did resolver see effective metadata?
                                       |
                                       +-- No --> retention/placement problem
                                       |
                                       +-- Yes --> did pointcut select it?
                                                       |
                                                       +-- No --> matcher problem
                                                       +-- Yes --> advice order,
                                                                  short-circuit,
                                                                  error/log filtering
~~~

Không sửa annotation một cách mù quáng khi thiếu log. Có thể lỗi nằm ở call path, pointcut hoặc telemetry, không phải annotation source.

## 13. Các ca kiểm thử cần có

| Trường hợp | Kỳ vọng |
| --- | --- |
| Required interface method được annotate đúng | PASS |
| Required method không có marker | Missing ERROR |
| Observe và IgnoreObserve cùng method | Conflict ERROR |
| Operation value rỗng hoặc invalid | Attribute ERROR |
| Marker trên private method ngoài proxy interface | Placement violation theo policy |
| Implementation-only annotation, resolver interface-only | Missing + warning/error về mismatch |
| @Target METHOD đặt trên FIELD | Compiler error |
| SOURCE retention nhưng runtime reflection cần marker | Không visible ở runtime |
| @Inherited trên class vs method override | Không suy inheritance sai |
| Two repeatable annotations | Resolver lấy đủ values |
| Meta-annotation | Chỉ effective nếu resolver hỗ trợ |
| Interface và implementation annotations khác nhau | Apply documented conflict policy |
| Method overloaded | Phân biệt signature, không chỉ tên |
| Bridge/synthetic methods | Đúng contract scope, không báo thiếu giả |
| Bytecode introduction thêm member | Kiểm đúng pre/post-weave representation |
| Direct call bypass proxy | Không nhận nhầm metadata false |
| Self-invocation trên target | Test actual coverage |
| Exception path | Advice giữ nguyên lỗi nghiệp vụ |
| Cache hit và short-circuit | Concern bắt buộc không bị bypass |
| Async completion | Không nhầm method return với completion |
| Concurrent invocations | Không dùng shared mutable currentAnnotation/currentUser |
| Validator bị skip | Không báo PASS giả |

Từng fixture nên có **oracle tường minh**: ID lỗi, severity, vị trí, số lần advice hoặc behavior cần có. Tránh lấy expected bằng cách gọi lại chính validator đang được test.

### 13.1 Test cả trường hợp hợp lệ

Một validator quá rộng có thể báo thiếu trên mọi helper và chặn cả những trường hợp được policy cho phép. Vì vậy luôn có:
- **Positive fixtures:** đủ metadata, không conflict, placement đúng.
- **Negative fixtures:** thiếu/mâu thuẫn/sai vị trí.
- **Boundary fixtures:** inheritance, repeatable, overloaded, generated methods, interface/implementation mismatch.
- **Behavior tests:** gọi qua proxy/weaving thực, quan sát advice và kiểm return/error.
- **Security tests:** mọi đường cần bảo vệ được enforcement, không chỉ có annotation.

## 14. Cảnh báo, lỗi và quản lý policy trong CI

| Phát hiện | Mức phù hợp |
| --- | --- |
| Thiếu annotation required cho bảo mật | ERROR, fail closed |
| Conflict trực tiếp giữa các lựa chọn bắt buộc | ERROR |
| Operation label không hợp lệ | ERROR |
| Annotation implementation-only khi resolver interface-only | WARNING hoặc ERROR nếu bắt buộc |
| Annotation legacy cần thay dần | WARNING |
| Annotation ở vị trí ngoài capability đã biết | ERROR nếu intent chắc chắn không thể áp dụng |
| Có khả năng false positive | Xem lại rule, không tự fail toàn bộ |

Trong CI:
1. Compiler kiểm syntax/@Target.
2. Source/type analyzer kiểm completeness và các ràng buộc tĩnh.
3. Weaving/instrumentation tạo cấu trúc thực nếu được dùng.
4. Validator đọc **effective metadata** sau biến đổi khi contract cần.
5. Integration tests xác minh **thực thi advice**.
6. Không đóng gói artifact khi mandatory checks fail.

Kiểm **clean** và **incremental build**, đặc biệt khi metadata đến từ generated sources, bridge methods hoặc structural introductions. Kết quả PASS khi checker bị skip **không hợp lệ** như bằng chứng kiến trúc.

## 15. Bảo mật và dữ liệu nhạy cảm

Một annotation tên @RequiresPermission không tự thực thi authorization. Nếu system cho phép bỏ qua một required marker hoặc một outer cache có thể short-circuit trước kiểm quyền, có thể tạo đường bypass.

Biện pháp:
- Giữ ranh giới kiểm quyền **đáng tin**, không phụ thuộc duy nhất vào metadata tự nguyện.
- Required annotation thiếu hoặc conflict phải fail theo policy.
- Không dùng logging guard để skip security/audit bắt buộc.
- Kiểm identity từ nguồn đã xác thực, không dùng trace ID hoặc tenant string tự khai làm authority.
- Không log raw args, tokens, password, PII hoặc payload chỉ để chẩn đoán validator.
- Với async, phân biệt return stage với successful completion và quyền còn hiệu lực lúc execute.
- Test cả direct/proxy calls, cache-hit, denied requests, exception và retry.

Validator bảo vệ **chất lượng cấu hình**; runtime checks bảo vệ **hành vi thực tế**.

## 16. Checklist kiểm tra annotation AOP

- [ ] Xác định chính xác API/type/method thuộc phạm vi bắt buộc.
- [ ] Định nghĩa missing policy và opt-out có được phép không.
- [ ] Định nghĩa các cặp mutually exclusive và precedence có thể chấp nhận.
- [ ] Kiểm attributes bắt buộc như operation nonblank và giới hạn dữ liệu.
- [ ] Đặt @Target đúng loại element; bổ sung semantic placement rules.
- [ ] Chọn @Retention theo nơi metadata sẽ được đọc.
- [ ] Quy định interface/implementation/superclass/type metadata resolution.
- [ ] Không dựa vào @Inherited cho method/interface annotations.
- [ ] Kiểm repeatable/composed annotations khi resolver hỗ trợ.
- [ ] Biết interception model: call, execution, proxy, weaving.
- [ ] Kiểm annotated private/static/helper/constructor có thật được instrument không.
- [ ] Báo diagnostic có ID, severity, method/signature và gợi ý.
- [ ] Test đầy đủ valid, missing, conflicting, misplaced và blank value.
- [ ] Test overload, override, inherited interface, bridge/generated methods.
- [ ] Test direct call, self-invocation, short-circuit, exception và async.
- [ ] Phân biệt metadata validation với runtime enforcement.
- [ ] Không cho annotation opt-out bỏ security/audit bắt buộc.
- [ ] Kiểm validator không bị skip trong CI; clean/incremental nhất quán.
- [ ] Đo overhead/caching của metadata resolver khi áp dụng rộng.
- [ ] Không giữ request-specific state trong shared aspect hoặc metadata cache.

## 17. Những nhầm lẫn thường gặp

1. **Có @Target(METHOD) nghĩa method phải public.** Sai, private method cũng là method.
2. **Có annotation là advice tự chạy.** Sai, cần pointcut và weaving/interception.
3. **RUNTIME retention nghĩa mọi method invocation được chọn.** Không, chỉ giúp reflection thấy marker ở element tương ứng.
4. **@Inherited tự áp dụng cho method override trong interface.** Không.
5. **Annotation ở interface tự xuất hiện trên implementation method.** Không.
6. **Một marker implementation-only luôn được JDK interface proxy đọc.** Không.
7. **Hai annotation trên method luôn mâu thuẫn.** Chỉ mâu thuẫn theo policy tương ứng.
8. **Java compiler tự bắt mọi semantic conflict.** Không, compiler chủ yếu kiểm quy tắc ngôn ngữ.
9. **Có Ignore marker là hợp lệ cho mọi concern.** Không với security/audit bắt buộc.
10. **Không có log đồng nghĩa method không có annotation.** Có thể coverage/pointcut/log sink sai.
11. **Bộ kiểm tra reflection là bytecode weaver.** Không.
12. **Một lần PASS chứng minh mọi đường gọi đã được bảo vệ.** Không.
13. **Một type-level marker tự được kế thừa xuống từng method.** Cần resolver policy.
14. **Repeatable annotation bắt buộc advice chạy nhiều lần.** Không.
15. **Static analyzer luôn biết mọi dynamic target/reflection call.** Không.
16. **Chỉ cần test vi phạm, không cần fixture hợp lệ.** Dễ false positives.
17. **Nhiều aspects có thể xếp theo thứ tự annotation xuất hiện.** Không.
18. **Shared aspect nên lưu currentUser trong field.** Có thể data race/cross-request contamination.

## 18. Câu hỏi ôn tập

**Câu 1.** Một interface đánh dấu @CheckedService nhưng có method không mang Observe hoặc IgnoreObserve. Theo policy ví dụ, lỗi nào?

**Đáp:** ARC001, missing annotation trên một operation thuộc required contract.

**Câu 2.** Một method có cả @Observe và @IgnoreObserve. Java có thể compile không?

**Đáp:** có thể compile vì hai annotation types đều cho METHOD; rule nghiệp vụ phát ARC002 conflict.

**Câu 3.** Annotation nằm trên private helper trong hệ thống chỉ dùng interface proxy. Đó là lỗi cú pháp Java?

**Đáp:** không. @Target(METHOD) cho phép, nhưng wrong placement theo interception boundary nên có thể phát ARC004.

**Câu 4.** Implementation method có @Observe, interface method tương ứng không có. Interface-only resolver có tự nhìn thấy implementation annotation không?

**Đáp:** không. Cần di chuyển annotation hoặc chọn resolver policy khác. Demo phát missing ARC001 và warning ARC005.

**Câu 5.** @Retention(RUNTIME) đảm bảo annotation trên implementation tự xuất hiện ở interface Method?

**Đáp:** không. Retention chỉ quyết định metadata có thể đọc ở đúng element; không sao chép giữa các method declarations.

**Câu 6.** @Inherited có truyền annotation trên interface method vào implementation override không?

**Đáp:** không, vì @Inherited không cung cấp semantics đó.

**Câu 7.** Một method có @Observe("") khác gì không annotate?

**Đáp:** có marker nhưng giá trị cấu hình sai, ARC003; không annotate mới là missing ARC001.

**Câu 8.** Nếu một required authorization annotation tồn tại nhưng lời gọi bypass proxy, có chắc quyền đã được kiểm không?

**Đáp:** không. Cần enforcement ở ranh giới đáng tin và integration tests cho đường bypass.

**Câu 9.** Validator startup PASS đủ để khẳng định advice được gọi cho method annotated chưa?

**Đáp:** chưa. Nó kiểm metadata; phải có behavior test trên interception boundary thật.

**Câu 10.** Khi một checker không phát error, nên hỏi gì trước?

**Đáp:** rule có chạy không, đã kiểm đúng source/effective metadata và đúng các method/join points trong contract chưa?

## 19. Kết luận

**Kiểm tra annotation thiếu, mâu thuẫn hoặc đặt sai chỗ** là bài toán thiết kế một **hợp đồng metadata** cùng khả năng can thiệp AOP thực sự:
- **Thiếu:** xác định element nào bắt buộc annotate.
- **Mâu thuẫn:** phát hiện policy và giá trị không tương thích.
- **Sai vị trí:** phân biệt Java @Target hợp lệ với method/type mà interceptor thật có thể quan sát.
- **Đúng nguồn:** giải rõ annotation ở interface, implementation, hierarchy và retention.
- **Đúng hành vi:** kiểm chứng join point, advice, result, exception, security và coverage.

**Ghi nhớ:** annotation là **ý định**, không tự là enforcement. Một metadata validator tốt ngăn cấu hình sai từ sớm, còn test interception/runtime mới chứng minh concern đã được áp dụng ở những đường thực thi cần thiết.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="annotation-metadata-009">Đánh đổi giữa cấu hình bên ngoài và đánh dấu trong mã</a>

<details>
<summary>Xem chi tiết</summary>

# Đánh đổi giữa cấu hình bên ngoài và đánh dấu trong mã

## 1. Hai cách bày tỏ ý định AOP, không phải hai loại phép màu

Trong **lập trình hướng khía cạnh (Aspect-Oriented Programming — AOP)**, ta muốn tách những mối quan tâm cắt ngang như tracing, audit, phân quyền, metrics, retry, validation hay caching khỏi phần lớn code nghiệp vụ. Một câu hỏi quan trọng của thiết kế là **đặt thông tin lựa chọn join point và chính sách can thiệp ở đâu**:

- **Đánh dấu trong mã (in-code annotation / declaration):** người viết source gắn dấu lên type, method hoặc một phần tử được hỗ trợ, chẳng hạn `@Observed` hoặc `@Sensitive`. Một bộ đọc metadata sẽ dùng dấu này khi áp dụng pointcut hoặc tạo cấu hình.
- **Cấu hình bên ngoài (external configuration):** rule về class, method, signature, annotation, loại join point, precedence hoặc policy nằm trong file cấu hình, metadata registry, bộ chọn tập trung, build configuration hoặc một lớp policy được triển khai riêng.

Ví dụ về ý định:

~~~text
CODE-CENTRIC:
   @Observed
   public Receipt pay(Request request) { ... }

EXTERNAL:
   "Observe execution of PaymentService.pay(Request)"
   "Include all methods matching PaymentService.*"
   "Exclude status/health methods"
~~~

Đây là **mô tả minh họa**, không phải cú pháp AOP được mọi engine hiểu. Hai cách mô tả có thể hướng đến **cùng một advice**, nhưng chúng tạo các tradeoff khác nhau về độ tường minh, tính gắn kết với source, phạm vi chính sách, việc refactor, khả năng sửa rules và nguy cơ match sai.

**Luận điểm cốt lõi:** annotation cũng như file cấu hình chỉ là **thông tin đầu vào**. Để advice thực sự chạy cần có **pointcut/matching rule** và **interception mechanism** như proxy, dispatcher, compile-time/binary weaving hoặc load-time transformation mà công cụ cụ thể hỗ trợ.

Không có annotation thì **vẫn có thể có AOP**. Có annotation nhưng không có mechanism thì **không tự có AOP**. Có file cấu hình bên ngoài nhưng không cài cơ chế can thiệp thì **cũng không tự có AOP**.

### Mục tiêu của bài

Sau bài học, bạn có thể:

1. Phân biệt rõ **rule location**, **metadata**, **join point matching** và **runtime interception**.
2. So sánh annotation trong code và cấu hình ngoài theo các tiêu chí thực tế.
3. Hiểu kiểu coupling, phạm vi policy và ảnh hưởng của refactor/versioning.
4. Thiết kế ranh giới trách nhiệm: tác giả code, chủ sở hữu security/audit, vận hành và build pipeline.
5. Mô tả khi nào sử dụng annotation, external config hoặc mô hình kết hợp.
6. Kiểm thử bằng Java 17 rằng cùng một target có thể bị chọn khác nhau mà không sửa code nghiệp vụ.
7. Tránh các ngộ nhận về hot reload, proxy coverage, self-invocation và các giới hạn runtime.
8. Xây bộ test chứng minh **có match đúng** và **có thực thi advice thật**, gồm cả negative coverage.

Bài tập trung vào **Aspect/AOP ở cấp cơ bản và nâng cao**, không gắn vào một framework ứng dụng cụ thể.

## 2. Bốn khâu cần tách ra

Một hệ thống AOP có thể được xem theo chuỗi:

~~~text
1. DECLARE
   annotation trong source, rule file, programmatic registry
           |
2. RESOLVE / MATCH
   type, method, metadata, signature, join point kind
           |
3. INSTALL INTERCEPTION
   tạo proxy, weave bytecode, register dispatcher
           |
4. EXECUTE
   khi join point xảy ra: before/after/around/proceed
~~~

**Dời khâu 1 từ source sang file cấu hình không tự làm thay đổi khả năng của khâu 3.** Ví dụ:

- Một JDK interface proxy chỉ quan sát các lời gọi đi qua proxy. Đổi rule từ annotation sang external method name **không làm nó tự can thiệp vào private method hoặc direct field access**.
- Một bytecode weaver có thể instrument join point mà proxy không thấy, nhưng nó vẫn có thể sử dụng annotation **hoặc** rule ngoài làm tín hiệu.
- Một compile-time weaver có thể cần rebuild khi rule thay đổi; một runtime dispatcher có thể hỗ trợ reload, nhưng chỉ khi đã thiết kế lifecycle và atomic snapshot phù hợp.

Đánh đổi chính là **cách biểu đạt và quản lý ý định**, không phải một sự phân chia tuyệt đối về sức mạnh kỹ thuật của interception engine.

### 2.1. Một cùng advice, hai nguồn rule

~~~text
                 RULE SOURCES
              /                 \
         SOURCE @Observed       external selectors
              \                 /
               \               /
                pointcut resolution
                       |
                 matched joinpoints
                       |
                   timing advice
                       |
                target executions
~~~

Nếu kết quả match cuối giống nhau và runtime mechanism giống nhau, về nguyên tắc before/after behavior có thể giống. Khác biệt sẽ nằm ở **cách thay đổi, kiểm soát và kiểm chứng rules**.

### 2.2. Đừng đồng nhất cấu hình ngoài với pointcut chuỗi đơn giản

External rules có thể là một dữ liệu cấu trúc có type-safe schema, ví dụ:

~~~yaml
policyVersion: 4
rules:
  - concern: metrics
    targetType: example.PaymentService
    method: pay
    parameterTypes:
      - example.Request
    effect: observe
  - concern: metrics
    targetType: example.PaymentService
    method: health
    effect: exclude
~~~

Đây là **ví dụ schema minh họa**, không phải cấu hình có thể dùng nguyên xi với mọi AOP engine. Nó cho thấy rule ngoài có thể mang version, owner và explicit exclusions, thay vì chỉ một chuỗi regex mong manh.

## 3. Annotation trong mã: ưu điểm về tính gần gũi với đối tượng được đánh dấu

Khi method có:

~~~java
@Observed
public String submit(String id) {
    return "saved:" + id;
}
~~~

đây là fragment Java, cần annotation `Observed` được định nghĩa phù hợp. Ý định “method này có metadata Observed” nằm **ngay cạnh chữ ký và logic** của method.

Lợi thế:

- **Discoverability:** developer mở file sẽ thấy dấu; code review dễ phát hiện thay đổi metadata khi sửa method.
- **Refactor support:** IDE/compiler có thể xử lý declaration gần code, giảm sai lệch do rename đối với một số workflows.
- **Intention ownership:** tác giả API có thể tuyên bố method này mang ý nghĩa cần quan sát.
- **Type-level documentation:** annotations có thể đi cùng elements, mặc định, retention, target, và rules khai báo.
- **Nhắm mục tiêu chính xác:** đánh dấu một method cụ thể thay vì pattern bao cả nhóm có quá nhiều false positives.
- **Bản build tái lập:** annotation ở source đi qua quy trình code review và version control như code.

Tuy nhiên, lợi ích không tự động biến thành **runtime guarantee**. Annotation chỉ có hiệu lực nếu component khác thực sự hiểu nó và cài mechanism. Reviewer vẫn phải xem interceptor/pointcut, đặc biệt với security và transaction concerns.

### 3.1. Coupling vào code nghiệp vụ

Đánh dấu `@Observed` làm một type nghiệp vụ **biết tới annotation type** ở cấp source/import/dependency. Điều này có thể chấp nhận được khi `Observed` thuộc hợp đồng miền, nhưng cũng có thể làm code nghiệp vụ phụ thuộc infrastructure metadata mà đội ngũ muốn tách rời.

Cần hỏi:

- Annotation nằm trong một module API ổn định hay library infrastructure tùy phiên bản?
- Domain object cần mang dependency đó để làm gì?
- Có muốn sử dụng target trong chương trình khác không có AOP không?
- Đổi concern có bắt buộc sửa rất nhiều source files không?
- Nếu annotation tên `RequiresAudit`, ai có quyền được xóa dấu này?

**Đánh dấu phân tán ở hàng trăm methods có thể tăng chi phí di trú policy**, dù mỗi dấu rất dễ nhìn thấy.

### 3.2. Ai chịu trách nhiệm đặt annotation?

Nếu compliance yêu cầu mọi thao tác cập nhật dữ liệu phải có audit, mà việc áp audit phụ thuộc developer tự nhớ thêm `@Audited` ở từng method, có thể xảy ra **coverage omission**: method mới không được đánh dấu, nên không được audit.

Một chính sách theo cấu trúc rộng hoặc kiểm tra build-time bắt buộc có thể giảm nguy cơ bỏ sót. Ngược lại, một external rule quá rộng lại có thể audit cả thao tác không phù hợp, ghi dư dữ liệu hoặc ảnh hưởng performance.

### 3.3. Chữ ký thay đổi và metadata có thể mất ngữ nghĩa

Khi method tách thành hai helper, `@Observed` nên ở public boundary, từng helper hay cả hai? Nếu developer chuyển annotation từ interface method sang implementation, scanner hiện tại có còn nhìn thấy không? Nếu rename method, annotation thường vẫn nằm cạnh declaration nhưng **join point coverage** có thể thay đổi do proxy/weaving limitations.

Annotation giúp giữ ý định sát code, không thay thế thiết kế lifecycle/semantics của advice.

## 4. Cấu hình bên ngoài: ưu điểm về quản lý chính sách tập trung

Rule được đặt bên ngoài class nghiệp vụ có thể phù hợp khi:

- **Một concern áp lên nhiều types** dựa trên pattern ổn định, ví dụ mọi API boundary trong một module.
- **Chủ sở hữu policy khác tác giả source**, như đội vận hành tracing hoặc bộ phận audit.
- **Không thể hoặc không muốn sửa source** của các thư viện/third-party code.
- **Muốn thay phạm vi quan sát** mà không thêm/sửa annotations trên hàng trăm methods.
- **Cần bật/tắt policy theo environment**, có audit trail và rollout.
- **Muốn giữ domain code độc lập** với annotation type của engine.

Ví dụ ý niệm:

~~~text
EXTERNAL RULE:
  Concern: metrics
  Include: public execution of type pattern *Service
  Exclude: health, readiness
  Configuration owner: observability team
  Config version: 9
~~~

Cách này giúp quản lý tập trung, nhưng cũng làm một behavior **không hiển thị trực tiếp trong file source của target**. Developer có thể mở `PaymentService.java` và không biết method sẽ chịu logging, retry hoặc authorization nếu không đọc external rules.

### 4.1. Cấu hình ngoài cũng tạo coupling

Không dùng annotation không có nghĩa “không coupling”. External config thường gắn vào **binary/class names, packages, signatures, annotation names, parameter types hoặc deployment artifact identifiers**.

Đổi `example.PaymentService.pay` thành `example.Payments.pay` có thể làm một rule cũ **không match gì**. Một regex dùng `*Service` có thể vô tình match class mới. Coupling chỉ **chuyển từ dependency source/import sang dependency cấu hình với cấu trúc chương trình**.

### 4.2. External không mặc nhiên hot-reload

Một external selector có thể được đọc:
- Khi **compile**: cần rebuild/reweave để rule đổi.
- Khi **class load**: cần classloader/restart hoặc retransform trong giới hạn công nghệ.
- Khi **proxy creation**: cần tạo lại proxy để có rule mới, nếu handler giữ immutable snapshot.
- Trên **mỗi invocation**: có thể đọc cập nhật mới nhưng cần concurrency, validity và cost control.

Vì vậy “đổi file config không cần deploy” chỉ đúng **nếu kiến trúc cài rule hỗ trợ và có phạm vi rõ**. Nếu không, chỉ đổi nội dung file trên disk có thể không thay behavior runtime.

### 4.3. Quy tắc ngoài quá rộng gây blast radius

Một selector `all methods named save` có thể vô tình chọn:
- Method nội bộ và helper có cùng tên.
- Methods xử lý dữ liệu nhạy cảm không nên log payload.
- Methods được gọi tần suất cao gây overhead lớn.
- Đường cache/retry mà semantics khác mong muốn.
- Method mới được thêm ở version sau.

Rule rộng thuận tiện khi cần **default coverage**, nhưng bắt buộc có exclusions, tests và policy về thay đổi API.

## 5. Bảng đánh đổi toàn diện

| Tiêu chí | Annotation trong code | Cấu hình bên ngoài |
| --- | --- | --- |
| **Nhìn thấy ý định** | Dễ nhìn ngay trên declaration | Phải truy vết rule registry/file |
| **Phạm vi cập nhật** | Sửa một hoặc nhiều source files | Có thể sửa một policy trung tâm |
| **Phụ thuộc source** | Target phải biết annotation type | Target có thể không cần import annotation |
| **Coupling** | Với annotation API/metadata | Với names, signatures, package patterns |
| **Độ chính xác ở method** | Dễ đánh dấu trực tiếp | Cần selector đủ chặt và resolver đúng |
| **Tự phủ class mới** | Class mới cần được đánh dấu hoặc meta-rule | Pattern có thể tự match class mới |
| **Nguy cơ bỏ sót** | Developer quên annotation | Config không cập nhật khi rename/move |
| **Nguy cơ áp quá rộng** | Ít nếu đánh dấu hẹp | Cao nếu selector rộng và không kiểm soát |
| **Bên sở hữu policy** | Thường là code owner | Có thể tách cho policy/ops owner |
| **Thay theo môi trường** | Cần một cơ chế điều kiện riêng | Có thể dễ hơn nếu runtime hỗ trợ |
| **Refactoring** | IDE thường nhìn annotation trực tiếp | String selector/regex dễ lỗi khi rename |
| **Review / audit** | Theo source diff | Theo config diff/policy approvals |
| **Runtime overhead** | Phụ thuộc reader/interceptor | Cũng phụ thuộc reader/interceptor |
| **Khả năng dùng third-party types** | Không luôn sửa source được | Selector/weaver có thể bao code ngoài nếu hỗ trợ |
| **Rollback** | Gắn với source/rebuild/deploy | Tùy snapshot/reload/deployment contract |
| **Test cốt lõi** | Annotation discovery + interception | Rule validation + matching + interception |

Không thể quyết định chỉ bằng “annotation hiện đại hơn” hoặc “config linh hoạt hơn”. Phải cân nhắc **chủ sở hữu concern, độ rộng coverage, kỳ vọng deployment và hậu quả khi match sai**.

## 6. Rule trong mã cũng có thể tồn tại mà không dùng annotation

Một API Java có thể tự khai báo rule bằng code:

~~~java
boolean shouldObserve(Method method) {
    return method.getName().equals("submit");
}
~~~

Đây là fragment minh họa, `Method` cần import. Rule đó nằm trong mã nhưng **không phải metadata annotation**. Vì thế so sánh đúng không chỉ là “annotation vs XML/YAML”: có thể có **programmatic configuration**, aspect language declarations, policy registry, generated metadata và nhiều cách kết hợp.

Hai chiều độc lập:

~~~text
WHERE RULE IS STORED:
  source annotation / source selector / external file / central registry

HOW INTERCEPTION HAPPENS:
  proxy / dispatcher / compile-time weaving / binary weaving / load-time weaving
~~~

Một external file có thể chỉ chọn target cho compile-time weaver; một `@Observed` có thể được JDK proxy handler đọc. Không gộp hai trục này làm một.

## 7. Java 17 hoàn chỉnh: cùng target, hai nguồn lựa chọn advice

Ví dụ này dùng **JDK dynamic proxy**, không phải một engine AOP với ngôn ngữ pointcut riêng. Mục đích là minh họa **quy tắc chọn lời gọi** từ hai nguồn có thể khác nhau trong khi **cơ chế can thiệp và target giữ nguyên**.

Định nghĩa:

- `@Observed` có retention `RUNTIME` được gắn trực tiếp lên `Core.submit(String)`.
- `fromAnnotations()` chọn implementation methods có `@Observed`.
- `fromExternalNames(Set<String>)` dùng một tập tên method, đại diện cho **nội dung đã được load từ cấu hình ngoài**. Trong demo tập tên được truyền bằng `Set.of` để chương trình chạy không phụ thuộc file; trong ứng dụng thật bạn có thể parse file có schema/validation.
- `wrap()` dùng **cùng một JDK proxy handler**, chỉ khác `Rule` mà nó nhận.
- Hai proxy ngoài `externalV1` và `externalV2` thể hiện **hai snapshot cấu hình khác nhau**: V1 chỉ chọn `inspect`, V2 chỉ chọn `submit`. **Không có hot-reload tự động:** V2 được tạo thành proxy mới.
- Raw `core.submit("A")` không qua proxy, nên không chạy advice dù method có annotation.
- `byAnnotation.internal("E")` đi qua proxy cho method `internal` không đánh dấu; core gọi `this.submit("E")` nên nested call bỏ qua wrapper.

Tạo file `AspectSelectionDemo.java`:

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Set;

public final class AspectSelectionDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Observed {}

    interface Work {
        String submit(String id);
        String inspect();
        String internal(String id);
    }

    static final class Core implements Work {
        int submissions;

        @Observed
        public String submit(String id) {
            submissions++;
            System.out.println("CORE submit " + id);
            return "saved:" + id;
        }

        public String inspect() {
            System.out.println("CORE inspect");
            return "healthy";
        }

        public String internal(String id) {
            System.out.println("CORE internal");
            return this.submit(id);
        }
    }

    @FunctionalInterface
    interface Rule { boolean matches(Method implementation); }

    static Rule fromAnnotations() {
        return method -> method.isAnnotationPresent(Observed.class);
    }

    static Rule fromExternalNames(Set<String> methodNames) {
        Set<String> snapshot = Set.copyOf(methodNames);
        return method -> snapshot.contains(method.getName());
    }

    static Object invoke(Work target, Method method, Object[] args)
            throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException error) {
            throw error.getCause();
        }
    }

    static Work wrap(Work target, Rule rule, String label) {
        return (Work) Proxy.newProxyInstance(
            Work.class.getClassLoader(), new Class<?>[] { Work.class },
            (proxy, interfaceMethod, args) -> {
                Method actual = target.getClass().getMethod(
                    interfaceMethod.getName(), interfaceMethod.getParameterTypes());
                if (!rule.matches(actual)) return invoke(target, actual, args);
                System.out.println(label + " BEFORE " + actual.getName());
                Object value = invoke(target, actual, args);
                System.out.println(label + " AFTER " + value);
                return value;
            });
    }

    static void check(boolean value) {
        if (!value) throw new AssertionError("test failed");
    }

    public static void main(String[] args) {
        Core core = new Core();
        Work byAnnotation = wrap(core, fromAnnotations(), "ANNOT");
        Work externalV1 = wrap(core, fromExternalNames(Set.of("inspect")), "CONFIG_V1");
        Work externalV2 = wrap(core, fromExternalNames(Set.of("submit")), "CONFIG_V2");

        System.out.println("DIRECT " + core.submit("A"));
        System.out.println("ANNOT " + byAnnotation.submit("B"));
        System.out.println("ANNOT_INSPECT " + byAnnotation.inspect());
        System.out.println("CONFIG_V1_SUBMIT " + externalV1.submit("C"));
        System.out.println("CONFIG_V1_INSPECT " + externalV1.inspect());
        System.out.println("CONFIG_V2 " + externalV2.submit("D"));
        System.out.println("INTERNAL " + byAnnotation.internal("E"));
        check(core.submissions == 5);
        System.out.println("ASSERT submissions=5");
    }
}
~~~

Biên dịch bằng Java 17 trở lên:

~~~bash
javac --release 17 -encoding UTF-8 AspectSelectionDemo.java
java AspectSelectionDemo
~~~

Đầu ra thực tế **đã được kiểm chứng bằng `javac --release 17` và JVM**:

~~~text
CORE submit A
DIRECT saved:A
ANNOT BEFORE submit
CORE submit B
ANNOT AFTER saved:B
ANNOT saved:B
CORE inspect
ANNOT_INSPECT healthy
CORE submit C
CONFIG_V1_SUBMIT saved:C
CONFIG_V1 BEFORE inspect
CORE inspect
CONFIG_V1 AFTER healthy
CONFIG_V1_INSPECT healthy
CONFIG_V2 BEFORE submit
CORE submit D
CONFIG_V2 AFTER saved:D
CONFIG_V2 saved:D
CORE internal
CORE submit E
INTERNAL saved:E
ASSERT submissions=5
~~~

### 7.1. Vì sao cùng `Core.submit` lúc có advice, lúc không?

`core.submit("A")` gọi target trực tiếp, không có interceptor. `byAnnotation.submit("B")` đi qua proxy có rule kiểm `@Observed`, nên in before/after. `externalV1.submit("C")` vẫn đi qua proxy nhưng rule V1 không chọn `submit`, nên không in advice. `externalV2.submit("D")` cũng đi qua proxy nhưng rule V2 chọn `submit`, nên có advice.

**Cơ chế proxy giống nhau. Chính nguồn rule thay đổi tập lời gọi được chọn.** Và dù annotation hiện diện trên `submit` ở cả bốn cases, annotation không tự ép advice xuất hiện.

### 7.2. Vì sao `inspect()` được quan sát theo V1 nhưng không theo annotation?

`inspect()` không có `@Observed`. Rule annotation không chọn, nên `byAnnotation.inspect()` chỉ chạy core. Rule external V1 lại chứa tên `inspect` nên chọn nó, dù source `Core.inspect` không có annotation.

Đây là lợi thế của external selection khi không muốn sửa source. Đồng thời, nếu V1 bị cấu hình quá rộng hoặc sai, nó cũng có thể chọn một method không mong muốn.

### 7.3. Vì sao thay V1 sang V2 không sửa `Core`?

Dữ liệu rule được truyền vào `fromExternalNames` bằng immutable snapshot `Set.copyOf`. Khi tạo `externalV2`, chương trình cấp rule khác cho một **proxy instance mới**. Core method declarations và `@Observed` giữ nguyên.

**Không suy từ demo rằng file cấu hình production tự reload.** Thực tế muốn cập nhật policy đang chạy cần loader, validation, publication và lifecycle.

### 7.4. Vì sao `INTERNAL saved:E` không có advice?

`byAnnotation.internal("E")` đi qua proxy, nhưng method `internal` không có `@Observed` nên handler delegate cho core. Bên trong `Core.internal` là lời gọi `this.submit("E")` trên core, **không đi qua proxy**. Annotation vẫn nằm trên `submit`, nhưng interception không quan sát internal execution ấy.

Một bytecode weaver instrument execution join point thực có thể bao phủ self-call tùy capability; **đổi rule ngoài hay trong mã không tự chữa giới hạn của proxy**.

### 7.5. Vì sao chỉ tính `submissions=5`?

Các lượt submit là `A`, `B`, `C`, `D` và `E`. Advice có thể xuất hiện hoặc không, nhưng trong ví dụ mỗi đường đã gọi target đúng một lần, nên counter bằng 5. Điều này giúp tách **số lần business execution** và **số lần advice được áp dụng**.

### 7.6. Những giới hạn có chủ đích của demo

- External config được **mô phỏng bằng Set**, không parser file, không remote registry và không hot reload. Nó đủ để so *nguồn rule* nhưng không minh họa I/O config thật.
- Rule external so mỗi `method.getName()`, không phân biệt overloaded signatures. Một hệ thống thật cần selector chặt hơn.
- Handler không nhằm xử lý đầy đủ `equals/hashCode/toString`, access policy hoặc classloader/plugin corner cases.
- Demo minh họa interface proxy, không phải structural introduction hay field/constructor weaving.
- Rule V1/V2 được tạo cho cùng core, nên đọc/ghi core state chia sẻ; các proxy khác nhau không tự tạo transaction/isolation.
- `Set.copyOf` làm snapshot của names, không bảo đảm schema compatibility hoặc rule đã được xác minh.
- `println` dùng để nhìn rõ thứ tự events; trong ứng dụng thật, logging/metrics có lỗi, độ trễ và chính sách privacy riêng.

## 8. External rule cần schema và bộ kiểm tra nghiêm túc

Ví dụ chỉ chọn theo `method.getName()` là cố tình đơn giản. Trong production, hai overloads:

~~~java
String submit(String id);
String submit(String id, boolean urgent);
~~~

đều có cùng `getName() == "submit"`. Nếu rule chỉ nhắm một overload, cần biết:

- Declaring type / inherited method.
- Method name.
- Parameter types và return type khi thích hợp.
- Join point kind: call hay execution.
- Public/private/static/final và giới hạn engine.
- Method annotations, type annotations nếu rule cần.
- Classloader/module scope.
- Include/exclude precedence.

Một selector sai có thể gây **false positive** (chọn method không nên chọn) hoặc **false negative** (bỏ sót method cần được chọn).

### 8.1. Tránh dùng tên package như hợp đồng không đổi

Các tên package/type là phần cấu trúc mã, có thể đổi khi refactor. Nếu external rules ghép trực tiếp vào tên, cần kiểm rằng rename/move làm cập nhật policy. Có thể dùng marker interface, annotation ổn định hoặc một contract rõ hơn làm anchor, dù chúng cũng có tradeoff.

### 8.2. Cấu hình không hợp lệ phải có chính sách

Những lỗi cần phát hiện:
- Method/type không tồn tại trong version mới.
- Selector hợp lệ cú pháp nhưng match 0 join points ngoài mong muốn.
- Rule match quá nhiều join points.
- Điều kiện `include` và `exclude` mâu thuẫn.
- Hai policies có precedence không xác định.
- Value vượt mức cho phép hoặc thiếu parameter.
- Rule đòi join point kind mà current interceptor không hỗ trợ.
- External config tham chiếu classloader không tồn tại.

Có thể **fail-fast**, chặn rollout, cảnh báo hoặc dùng fallback tùy concern. Với authorization, lỗi rule mà âm thầm bỏ can thiệp có thể tạo lỗ hổng; với metrics, fail-open theo hợp đồng có thể được chấp nhận hơn.

## 9. Khi policy là bắt buộc: tính bao phủ quan trọng hơn chỗ đặt rule

Hãy phân biệt ba loại concern:

| Concern | Nếu bỏ sót một method | Chính sách thường cần cân nhắc |
| --- | --- | --- |
| Metrics best-effort | Mất một số quan sát | Alert/coverage report, đôi khi fail-open |
| Audit tuân thủ | Có thể mất bằng chứng quan trọng | Enforce tại boundary đáng tin, fail-closed hoặc durable audit design |
| Authorization | Có thể cho hành động trái quyền | Default-deny / policy-as-code, test bypass toàn diện |
| Retry | Có thể bỏ retry hoặc áp retry nguy hiểm | Chỉ chọn idempotent/transient paths |
| Cache | Có thể gọi business dư hoặc trả dữ liệu sai | Key/TTL/auth isolation và tests |

**Không có vị trí lưu rule nào tự đảm bảo coverage.**

- In-code annotation: có thể **quên đặt dấu**.
- External config: có thể **pattern sai hoặc stale sau refactor**.
- Cả hai: có thể **đúng selector nhưng runtime call bypass proxy**, class không woven hoặc advice bị short-circuit bởi layer khác.

Với những chính sách bảo mật quan trọng, cần thiết kế enforcement ở trust boundary, không chỉ “thấy annotation” hoặc “file config đã có dòng”.

### 9.1. Explicit opt-in vs default-on

- **Opt-in bằng annotation:** chỉ những methods được đánh dấu mới nhận concern. Kiểm soát hẹp, ít bất ngờ, nhưng dễ bỏ sót.
- **Default-on bằng external rule:** toàn bộ methods trong scope nhận concern trừ khi được exclude. Bao phủ rộng, nhưng tăng nguy cơ tác động tới internal helpers, methods sensitive hoặc performance-critical.
- **Hybrid:** rule rộng chỉ áp cho contract marker rõ; exceptions được quản lý có review; quan trọng nhất là có test phát hiện thiếu coverage.

Không có một câu trả lời chung. Hãy chọn theo **hậu quả của false negative so với false positive**.

## 10. Refactoring: ai sẽ phát hiện rule bị lệch?

### 10.1. Rename method khi annotation nằm trên method

Nếu developer đổi tên `submit` thành `placeOrder` nhưng giữ `@Observed` trên declaration mới, metadata vẫn nằm cạnh method. Tuy nhiên, runtime interception có thể khác nếu pointcut khác còn phụ thuộc tên cũ, proxy interface thay đổi hoặc class đã woven không được rebuild.

### 10.2. Rename method khi external rule theo tên

Config `"submit"` có thể **không match** method `placeOrder` mới. Source build vẫn thành công nếu không có kiểm rule; lỗi chỉ xuất hiện dưới dạng thiếu metrics/audit/authorization trong production.

Do đó cần **configuration-to-code validation** ở build/integration:
- Resolve selector trên artifact đã build.
- Assert expected matched-method set.
- Fail nếu mandatory rule có 0 matches.
- Assert không match ngoài type/module cho phép.
- So difference giữa matched sets của hai versions.

### 10.3. Signature changes và overloads

Một rule đặt bằng method name có thể không biết parameter types thay đổi. Annotation gắn cùng declaration có thể theo refactor tốt hơn, nhưng nếu method được chia tách, annotation không tự chọn đúng boundary. Dù lựa chọn nguồn rule nào, **chủ sở hữu business contract phải quyết định lại ý nghĩa can thiệp** sau refactor lớn.

## 11. Versioning: rule cũng là một phần của bản phát hành

Một application version không chỉ bao `.java`/`class` mà còn gồm:

~~~text
Runtime behavior =
  application artifact version
+ aspect/advice implementation version
+ pointcut/config version
+ proxy/weaver/agent version
+ classloader and deployment topology
~~~

Nếu code mới chạy với config cũ, external rule có thể bỏ lỡ methods đã đổi tên hoặc match nhầm methods mới. Nếu code cũ chạy với config mới, rule có thể tham chiếu API chưa tồn tại.

Với annotations, code và metadata thường đi cùng commit/build, nhưng advice implementation có thể nằm ở module khác với version riêng. Cả hai mô hình vẫn cần compatibility policy.

### 11.1. Configuration drift giữa môi trường

Dev có thể bật tracing, staging tắt và production bật audit bắt buộc. Nếu không có **environment matrix**, team có thể test một đường khác đường sẽ release. Metadata annotation có thể giống nhau nhưng **cách diễn giải** bị external settings điều chỉnh.

Một bảng kiểm:
- Rules đang active ở từng môi trường.
- Danh sách join points được match.
- Thứ tự aspects/advice.
- Runtime mechanism và classloader.
- Policy về missing rule/unknown field.
- Tín hiệu giám sát sau rollout.

### 11.2. Version rules cùng artifact hay độc lập?

Cấu hình ngoài độc lập có thể cho phép vận hành linh hoạt; nhưng càng tách lifecycle, càng cần:
- Schema version.
- Minimum/maximum compatible application version.
- Signature validation.
- Owner approval và change log.
- Rollback semantics.
- Canary/shadow matching để phát hiện coverage trước khi apply.

## 12. Khi cần hot reload cấu hình động

Nếu ứng dụng muốn đổi selectors khi đang chạy, cần hiểu **hai việc độc lập**:

1. Thay đổi **rule data**.
2. Đảm bảo **cơ chế interception có thể quan sát tập join points mới**.

Một proxy đã tạo có handler đọc rule qua một `AtomicReference<ImmutableRuleSet>` có thể cho phép đổi policy matching trên **những methods đã đi qua proxy**. Nhưng nó **không làm raw target calls bỗng đi qua proxy**, cũng không tự instrument constructor/field/private execution.

Một compile-time weaver đã chỉ weave một số methods có thể không có hook ở một method ngoài tập đó. Thay rule ngoài để chọn method mới **không giúp method ấy có advice hook nếu chưa được instrument**.

### 12.1. Snapshot rule không được half-applied

Với dynamic config nên:

~~~text
read and parse candidate config
         |
validate entire schema + references
         |
compute immutable RuleSet version N+1
         |
publish atomically to dispatcher
         |
old in-flight invocations -> policy snapshot N, nếu hợp đồng chọn vậy
new invocations -> policy snapshot N+1
~~~

Đây là **mô hình thiết kế**, không phải API sẵn có. Nếu dispatcher kiểm các mutable fields bị thay từng phần, hai requests có thể thấy rule hỗn hợp nửa cũ nửa mới.

Ngoài ra phải xác định **thời điểm chụp snapshot**: lúc proxy call bắt đầu, mỗi join point, hay mỗi attempt trong retry? Nếu một operation gồm nhiều calls và policy đổi giữa chừng, có thể cần version pinning theo logical request.

### 12.2. Rollback và quyền thay đổi rules

Nếu đổi external rule có thể làm bật/tắt authorization, quyền sửa config tương đương **quyền thay policy bảo mật**. Cần authentication/authorization cho control plane, code review hoặc approvals, event audit, immutable histories và rollback an toàn.

Không biến “cấu hình không cần code deploy” thành “bất kỳ người có quyền ghi file đều có thể tắt security concern”.

### 12.3. Cấu hình theo tenant/request

Một policy theo tenant cần làm rõ nguồn tenant identity đáng tin và isolation. Không đặt global mutable `currentTenant` để matcher dùng cho nhiều threads. Có thể truyền request context hoặc resolved policy theo invocation, nhưng lại phải xử lý ThreadLocal cleanup/async context propagation.

**Rule location không giải quyết thread safety hoặc security context**; đó là trách nhiệm riêng.

## 13. Mô hình kết hợp: annotation làm tín hiệu ổn định, config làm chính sách

Một phương án thường dễ bảo trì:

~~~text
SOURCE:
    @BusinessOperation
    public Result execute(...)

EXTERNAL:
    select methods annotated @BusinessOperation
    attach metrics advice in production
    attach test observer in test environment
    configure sampling rates and excludes
~~~

Ví dụ là **mô tả kiến trúc**. Annotation định nghĩa **semantic contract ổn định**: đây là một business operation; config quyết định **concern nào có hiệu lực và mức nào**. Hai bên chia trách nhiệm.

Ưu điểm:
- Giảm coupling rule với method names.
- Đỡ phải sửa code khi đổi sampling/observability policy.
- Dễ giới hạn class set bằng metadata đã review.
- Cho phép công cụ tập trung phát hiện annotations trên artifact.

Rủi ro:
- Cần hai chỗ để hiểu behavior: source và policy.
- Annotation phải được tạo với retention/target phù hợp consumer.
- Người viết method vẫn có thể quên annotation.
- Rule ngoài vẫn có thể bị vô hiệu hóa hoặc thiếu ở môi trường.
- Meta-annotation/interface inheritance behavior phải được định nghĩa rõ.
- Nếu sử dụng security rules, default-deny và coverage tests vẫn phải có.

**Hybrid không tự an toàn hơn**; nó chỉ chia rõ loại ý định và nhóm người quản lý.

### 13.1. Phân biệt marker annotation và behavior configuration

Một marker `@BusinessOperation` không nên được mô tả như “tự tạo metrics”; nó chỉ nói **method thuộc một tập có ý nghĩa miền**. Rule ngoài có thể ánh xạ marker sang advice và parameterize mức thu thập dữ liệu.

Điều này cũng giúp tránh đổi public code contract chỉ để thay một tỷ lệ sampling.

## 14. Tại sao annotation không nên chứa mọi tham số policy?

Một annotation có thể có elements:

~~~java
@Observed(level = "verbose", captureArguments = true)
public Receipt pay(Request request) { ... }
~~~

Đây là fragment minh họa, `Observed` phải khai báo các elements tương ứng. Nếu những tham số này liên quan secrets, external endpoints, sampling operational knobs hoặc tenant-specific settings, hardcode vào source dễ dẫn đến:
- Bắt buộc rebuild/deploy chỉ để thay runtime knob.
- Mỗi environment cần behavior khác nhưng bytecode annotation cố định.
- Có nguy cơ lộ thông tin qua classfile/reflection.
- Bùng nổ annotation attributes làm API khó ổn định.

Một hướng: annotation thể hiện **ý nghĩa ổn định**, external policy quyết định **tần suất, destination, filters hoặc quyền bật/tắt** trong giới hạn security contract.

Ngược lại, nếu policy cần được review như source vì ảnh hưởng bảo mật, đưa toàn bộ thông số ra một dynamic registry không có kiểm soát cũng là lựa chọn nguy hiểm.

## 15. Đánh đổi về kiểm thử và debugging

### 15.1. Với in-code annotation

Các test quan trọng:
- `@Target`/`@Retention` đúng.
- Annotation có mặt trên **đúng element**.
- Reader kiểm interface hay implementation, method hay class?
- Có support `@Inherited` hoặc meta-annotations không?
- Overloaded methods không bị chọn nhầm.
- Annotation có mặt nhưng **interceptor không chạy** trên raw/bypass paths.
- Thay annotation value có đổi behavioral contract như dự định không?

### 15.2. Với external configuration

Các test quan trọng:
- Parser/schema validation.
- Tất cả named types/methods resolve được trên artifact.
- Rule match **đúng tập** join points, không chỉ match nonempty.
- Renames làm tests fail rõ.
- Include/exclude precedence hợp lý.
- Rule version tương thích runtime artifact.
- Hot reload hoặc snapshot publication đúng nếu hỗ trợ.
- Failure policy khi config không đọc được/không hợp lệ.
- Different environments không lệch ngoài chủ đích.
- Unauthorized config changes bị chặn và audit.

### 15.3. Với cả hai

- Test proxy/weaving đã **được cài thực**, không chỉ gọi matcher thuần.
- Test đường có advice và đường **không nên có advice**.
- Test exception và finally cleanup.
- Test short-circuit/retry không đổi kết quả nghiệp vụ ngoài hợp đồng.
- Test async completion và ThreadLocal/request context nếu dùng.
- Test raw vs proxy target, self-invocation, loaders.
- Test chạy đúng artifact phát hành thay vì bản raw.
- Đo overhead/p95-p99 và số join points instrument.

**Đọc được annotation hoặc validate được file config chưa phải integration test của AOP.**

## 16. Advice ordering không phải một vấn đề cú pháp đơn thuần

Một application có thể dùng các concerns:

~~~text
Authorize
  -> Cache
      -> Transaction
          -> Audit
              -> Target
~~~

Thứ tự outer/inner có thể ảnh hưởng:
- Kiểm quyền xảy ra trước hay sau cache hit.
- Audit ghi attempted, returned hay committed.
- Retry ở trong hay ngoài transaction.
- Lock scope có giữ qua network I/O không.
- Exception nào được advice phía ngoài quan sát.

Annotation ngay trên method có thể ghi `@Authorized` và `@Cached` nhưng **thứ tự runtime không tự suy được** nếu không có precedence rule. External config có thể diễn đạt thứ tự tường minh, nhưng rule không được validate vẫn dễ thay đổi behavior ngoài ý muốn.

**Chỗ đặt rule không thay thế việc kiểm order và boundaries.**

## 17. Những sai lầm khi nghĩ “annotation dễ refactor hơn mọi external rule”

Annotation thường theo declaration, nhưng vẫn có các tình huống:
- Annotation nằm trên **interface method**, implementation ở class khác.
- Overriding method không tự inherit method annotation theo mọi kiểu reflection lookup.
- Method được refactor sang helper khiến annotation không còn trên public boundary cần bảo vệ.
- Code generation tạo phương thức mới không copy metadata mong đợi.
- Classloader nạp artifact cũ chưa chứa annotation.
- Annotation `SOURCE` bị mất nếu reader chạy runtime.
- Một type-level marker được scanner hiểu khác giữa hai module.

Vì vậy annotation giảm một số **name-string drift** nhưng không miễn trừ integration tests.

## 18. Những sai lầm khi nghĩ “config ngoài dễ bật tắt tuyệt đối”

Nếu một external rule được cập nhật ở runtime, có thể gặp:
- Proxy handler vẫn giữ snapshot cũ.
- Weaver không instrument methods mới được chọn.
- Cache pointcut match chưa invalidated.
- Nhiều nodes nhận config mới vào các thời điểm khác nhau.
- Một request đang chạy thấy hai phiên bản policy.
- Rule update giữa hai retry attempts.
- Config mới yêu cầu join point kind công cụ không hỗ trợ.
- Security policy bị tắt ở một node khi node khác vẫn enforce.

**Dynamic policy changes là một vấn đề distributed rollout/version consistency**, không chỉ một lệnh sửa file.

Khi behavior bắt buộc phải thống nhất trên nhiều instances, cân nhắc coordinated rollout, version telemetry, canary tests và chế độ an toàn khi policy mismatch.

## 19. Giảm chi phí runtime và rủi ro performance

Không có quy tắc annotation matching luôn nhanh hơn external matching hoặc ngược lại. Chi phí thường đến từ:
- Số join points đã instrument.
- Thời gian resolve `Method` và đọc metadata.
- Chi phí parse/compile external selectors.
- Cache matching table đúng classloader/version.
- Thời gian thực thi advice (logging, metrics, network, lock).
- Số lần `proceed` do retry hoặc nested interception.
- Allocation per invocation / context propagation.

Một chiến lược:
1. Parse, validate và resolve selector lúc startup/build nếu phù hợp.
2. Cache immutable match decisions theo method/signature/classloader.
3. Không scan annotation graph hoặc regex từ đầu trong mọi hot invocation nếu không cần.
4. Không nắm một global lock xuyên `proceed` chỉ để đọc config.
5. Với live policy, dùng snapshot immutable có version và publication an toàn.
6. Theo dõi size/eviction của cache khi loaders động có thể unload.
7. Benchmark baseline không aspect, annotation selection và external selection trên **cùng target/workload**.

**Điểm quan trọng hơn một microbenchmark matcher là tính đúng của behavior**. Một rule sai chọn 10.000 methods thay vì 100 methods có thể gây overhead lớn dù mỗi lần kiểm chỉ vài nanoseconds.

## 20. Quy trình lựa chọn cho dự án

### Trường hợp A: người viết method muốn khai báo intent rõ

Chọn annotation khi:
- Ý nghĩa ổn định thuộc API/domain contract.
- Có quyền sửa source.
- Team muốn các dấu được review cùng code.
- Phạm vi nhỏ, từng method có vai trò rõ.
- Việc quên dấu được kiểm bằng tests hoặc static analysis.

Ví dụ: `@DomainCommand` làm metadata cho các method được xem là thao tác miền; policy bên ngoài có thể xác định logging/metrics theo group đó.

### Trường hợp B: policy chung do nhiều bên quản lý

Cân nhắc external config khi:
- Mọi method trong một phạm vi cần concern đồng nhất.
- Không có quyền sửa source thư viện.
- Team observability muốn đổi sampling hoặc exclusions.
- Cần approvals/rollout/audit ở policy control plane.
- Có kiểm thử selector drift và deployment compatibility.

### Trường hợp C: security hoặc audit bắt buộc

Đừng quyết chỉ theo tiện lợi.
- Xác định **trust boundary thật sự**.
- Định nghĩa default-deny/fail-closed khi appropriate.
- Test tất cả entry paths, kể cả raw and self-invocation.
- Ghi rõ coverage trên artifact runtime.
- Cân nhắc đặt bắt buộc ở tầng không thể bypass dễ dàng.
- Tách metadata khai báo, enforcement mechanism và resource transaction.

### Trường hợp D: cần chia code-domain intent với runtime operations

Cân nhắc hybrid:
- Annotation cho semantic grouping ổn định.
- External policy cho metrics sampling, destinations, allow/exclude lists.
- Immutable versioned snapshots với validation.
- Negative coverage tests.
- Một công cụ báo tập joinpoints được match ở mỗi release.

## 21. Decision matrix thực tế

| Câu hỏi | Nếu trả lời “có” | Hướng cần xem xét |
| --- | --- | --- |
| Source là của mình và intent cần hiển thị cạnh method? | Có | Annotation trong code |
| Cần instrument library mà mình không thể sửa source? | Có | External selector, nếu weaver/proxy coverage hỗ trợ |
| Concern áp dụng mặc định lên cả module? | Có | External scope + exclusions hoặc marker contract |
| Policy thay đổi thường xuyên theo vận hành? | Có | External config có reload/rollout được thiết kế |
| Đây là điều kiện an ninh bắt buộc? | Có | Enforce tại trust boundary, test bypass; không tin vào dấu tự nó |
| Rule theo method names dễ bị refactor? | Có | Annotation/marker, typed rules hoặc generated validation |
| Muốn mọi developer thấy behavior dự kiến khi đọc source? | Có | Annotation/documented contract |
| Muốn tránh phụ thuộc domain source với infrastructure annotation? | Có | External rules hoặc domain-stable marker |
| Có nhiều môi trường khác nhau? | Có | Tách semantic intent khỏi operational knobs |
| Không thể chấp nhận silent no-match? | Có | Fail-fast với assertion matched set |
| Có toolchain weaving ổn định và ABI tests? | Có | Có thể tận dụng rule mạnh, nhưng vẫn test |
| Chỉ cần theo dõi một hoặc hai operations? | Có | Cách đơn giản, tường minh, dễ test nhất |

Không cần cố chọn một phương án cho toàn bộ application. Từng concern có risk/cost khác nhau và có thể dùng chiến lược khác nhau.

## 22. Checklist triển khai

### Xác định contract

- [ ] Viết rõ concern nào, business boundary nào cần AOP.
- [ ] Phân biệt annotation metadata, selector rule và interception mechanism.
- [ ] Chọn source annotation, config ngoài hoặc hybrid với lý do cụ thể.
- [ ] Ghi owner quyền sửa policy và người chịu trách nhiệm review.
- [ ] Ghi expected joinpoints, positive coverage và negative coverage.

### Với annotation

- [ ] `@Target`, `@Retention` đúng consumer.
- [ ] Annotation trên đúng interface/implementation/type/method.
- [ ] Metadata inheritance/meta-annotation/repeatable policy rõ.
- [ ] Đổi declaration/refactor làm tests fail nếu coverage mất.
- [ ] Không nhầm annotation với security hoặc transaction guarantee.
- [ ] Không nhồi secrets/runtime knobs vào annotation values.

### Với external config

- [ ] Có schema, version và validation.
- [ ] Selector phân biệt overloads/signatures phù hợp.
- [ ] File không match hoặc match quá rộng bị phát hiện.
- [ ] Include/exclude và advice precedence được định nghĩa.
- [ ] Config-to-code compatibility check chạy trong CI.
- [ ] Reload/snapshot semantics được mô tả chính xác.
- [ ] Quyền cập nhật config có kiểm soát và audit trail.
- [ ] Có rollback plan cho rules.
- [ ] Môi trường phát hành dùng policy artifact đúng version.

### Với cơ chế can thiệp

- [ ] Biết rõ join point kind và coverage của proxy/weaver.
- [ ] Test gọi qua proxy vs raw target.
- [ ] Test self-invocation, nested calls, exception/finally.
- [ ] Test request đồng thời và async context nếu cần.
- [ ] Tránh duplicate advice/retry/side effects không mong muốn.
- [ ] Kiểm target classloader và build/deploy provenance.
- [ ] Theo dõi matched set, errors, p95/p99, overhead và cache.

## 23. Các nhầm lẫn phổ biến

1. **“Có annotation tức AOP tự chạy.”** Sai: annotation chỉ là metadata.
2. **“Có file config là proxy tự xuất hiện.”** Sai: interception mechanism phải được cài.
3. **“Không dùng annotation thì không thể viết AOP.”** Sai: structural, signature hoặc flow pointcuts có thể dùng nguồn khác.
4. **“Rule ngoài luôn cần ít maintenance.”** Nó có thể stale khi refactor và yêu cầu validation.
5. **“Annotation luôn tốt hơn config về bảo mật.”** Developer có thể bỏ sót hoặc code bypass interceptor.
6. **“External config luôn hot-reload.”** Tùy build/load/proxy lifecycle và publication.
7. **“Đổi V1 sang V2 trong demo nghĩa đổi file runtime.”** Demo tạo **proxy mới** với snapshot khác.
8. **“Source metadata gần code nghĩa runtime coverage đầy đủ.”** Có thể thiếu weaver/proxy và self-call.
9. **“Cấu hình theo method name phân biệt overloads.”** Không nếu không xét parameters.
10. **“Rule match đúng là đủ.”** Còn phải intercept đúng actual invocation.
11. **“Không có annotation nghĩa method không thể được instrument.”** Có thể chọn qua external rule.
12. **“External rule chọn class final thì subclass proxy luôn dùng được.”** Cơ chế proxy có giới hạn riêng.
13. **“Annotation ở interface tự là annotation ở implementation.”** Lookup semantics cần xác định.
14. **“Tất cả loại join point được hỗ trợ chỉ vì config mô tả được.”** Tool có giới hạn capability.
15. **“Một wildcard mới chỉ ảnh hưởng method hiện tại.”** Nó có thể match classes/methods được thêm về sau.
16. **“Config owner không cần code review vì chỉ thay text.”** Config có thể thay bảo mật/hành vi quan trọng.
17. **“Annotation review là đủ, không cần test.”** Chưa chứng minh advice thực sự chạy.
18. **“Thay policy giữa hai requests luôn atomic.”** Cần immutable snapshot/publication.
19. **“ThreadLocal current config tự lan qua executor.”** Không.
20. **“Trình đọc metadata runtime tự thấy CLASS/SOURCE annotations.”** Retention quyết định khả năng reflection.
21. **“Một record cache matcher có thể dùng bất kể classloader.”** Có thể sai type identity và leak loaders.
22. **“Tăng thread count sửa overhead của mọi advice.”** Global lock/I/O có thể vẫn nghẽn.
23. **“Trong code là static, bên ngoài là dynamic.”** Đây là hai trục độc lập.
24. **“Một proxy có thể can thiệp mọi private/self-call.”** JDK interface proxy chỉ thấy đường caller qua proxy.
25. **“Rule source đã đúng thì cả hệ thống không bị bypass.”** Need test mọi entry path.
26. **“Fail-open luôn ổn khi annotation/config bị thiếu.”** Không với authorization bắt buộc.
27. **“Sampling rates nên hardcode vào annotation ở mọi phương thức.”** Có thể làm migration vận hành khó.
28. **“Muốn thêm advice cho methods chưa woven chỉ cần reload config.”** Hook có thể chưa tồn tại.
29. **“Bản config mới chạy với binary cũ chắc phù hợp.”** Signature/version drift có thể phá matching.
30. **“`Set.copyOf` tự động kiểm business validity của rules.”** Nó chỉ sao chép collection theo contract Java.

## 24. Câu hỏi tự kiểm tra

**Câu 1.** Annotation và external config giống nhau ở điểm nào?  
**Đáp án:** Cả hai có thể làm nguồn dữ liệu để chọn join points; không tự chạy advice.

**Câu 2.** Vì sao `core.submit("A")` không có advice dù `Core.submit` có `@Observed`?  
**Đáp án:** Vì caller đi thẳng tới raw target, không qua proxy.

**Câu 3.** `externalV1.inspect()` có advice mà `byAnnotation.inspect()` không, vì sao?  
**Đáp án:** V1 chọn tên `inspect` qua rule ngoài, trong khi implementation method không có annotation `@Observed`.

**Câu 4.** Đổi method name có rủi ro nào với external selector?  
**Đáp án:** Selector cũ có thể match 0 methods hoặc chọn nhầm; cần validation và matched-set tests.

**Câu 5.** Annotation làm security tự an toàn không?  
**Đáp án:** Không. Cần enforcement tại trust boundary và test bypass.

**Câu 6.** Có thể thay config runtime để JDK interface proxy intercept private methods không?  
**Đáp án:** Không chỉ bằng config; proxy capability không hỗ trợ interception private execution.

**Câu 7.** Vì sao demo tạo `externalV2` thay vì sửa `externalV1`?  
**Đáp án:** Vì `Rule` dùng immutable snapshot; demo không thiết kế hot reload, nên tạo wrapper với policy khác để so sánh.

**Câu 8.** Có nên đọc YAML và compile regex ở mỗi invocation không?  
**Đáp án:** Thường không; parse/validate sớm, cache decisions phù hợp và đo overhead.

**Câu 9.** Hybrid model có giá trị gì?  
**Đáp án:** Marker annotation có thể ghi ý nghĩa ổn định ở source, external policy điều khiển knobs/concerns thay đổi theo vận hành.

**Câu 10.** Nếu rule ngoài match 0 methods nhưng syntax vẫn hợp lệ, có nên coi là thành công?  
**Đáp án:** Không mặc định; với policy bắt buộc cần fail-fast hoặc cảnh báo nghiêm trọng theo contract.

**Câu 11.** Khi có hai proxy cùng bao một core, việc chọn rule khác có làm hai cores xuất hiện?  
**Đáp án:** Không trong demo; cả hai delegate cùng core, `submissions=5` bao tổng lần thực thi.

**Câu 12.** Tại sao `this.submit("E")` không in advice?  
**Đáp án:** Internal call trên target không qua proxy, dù method được đánh dấu.

**Câu 13.** External config có thể tạo nguy cơ ngoài performance?  
**Đáp án:** Có, policy/bảo mật, dữ liệu nhạy cảm, version mismatch và thay đổi semantic.

**Câu 14.** Điều kiện nào cần trước khi tuyên bố “config không cần deploy”?  
**Đáp án:** Cần mechanism hỗ trợ runtime reload, atomic policy publication và coverage của join points được lựa chọn mới.

## 25. Tổng kết

**Đánh dấu trong mã** nhấn mạnh **ý định nằm sát declaration**, có lợi cho discoverability, code review và chọn method chính xác; đổi lại tăng phụ thuộc metadata vào source, cần sửa nhiều nơi khi chính sách thay đổi và có nguy cơ quên đánh dấu. **Cấu hình bên ngoài** nhấn mạnh **quản lý tập trung và tính điều chỉnh**, hỗ trợ crosscutting theo module/third-party code hoặc operational policy; đổi lại dễ lệch khi đổi signature/package, cần schema validation, chủ sở hữu policy và kiểm soát rollout/rollback.

Cả hai chỉ là **nguồn lựa chọn**. **Pointcut resolution** và **interception runtime** mới quyết định advice có thực sự chạy hay không. Ví dụ Java 17 đã xác minh cùng `Core` có thể được quan sát khác nhau khi chọn rule annotation hoặc rule ngoài, và việc có annotation trên `submit` không làm raw/self calls tự được can thiệp.

Lựa chọn tốt xuất phát từ **contract, mức độ quan trọng của concern, quyền quản lý policy và khả năng kiểm thử**, không phải sở thích cú pháp. Đối với hệ thống lâu dài, thường nên tách **semantic intent ổn định** khỏi **operational knobs dễ thay đổi**, gắn version cho rules và **kiểm cả positive lẫn negative join point coverage trên artifact thật**.

</details>

- [Quay lại đầu trang](#back-to-top)
