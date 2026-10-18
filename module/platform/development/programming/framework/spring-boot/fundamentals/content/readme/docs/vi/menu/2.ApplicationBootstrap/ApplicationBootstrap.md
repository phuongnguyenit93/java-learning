<a id="back-to-top"></a>

# Khởi động ứng dụng với SpringApplication

## Menu
- [SpringApplication là gì và vì sao là điểm vào của Boot?](#springapplication-role)
- [SpringApplication.run biến main thành ApplicationContext như thế nào?](#main-to-context)
- [Quá trình khởi động diễn ra ở mức tổng quan ra sao?](#bootstrap-flow)
- [Ứng dụng đi qua các giai đoạn khởi động, chạy và dừng có trật tự thế nào?](#high-level-lifecycle)
- [Mô hình vòng đời trong Fundamentals dừng ở đâu?](#runtime-handoff)

## <a id="springapplication-role">SpringApplication là gì và vì sao là điểm vào của Boot?</a>

<details>
<summary>Xem chi tiết</summary>

`SpringApplication` là lớp trừu tượng Boot dùng để bootstrap ứng dụng. Một chương trình Java thông thường bắt đầu tại `main`; ứng dụng Boot vẫn giữ điểm vào Java đó nhưng giao việc tạo và khởi động ứng dụng Spring cho `SpringApplication`.

Dạng phổ biến rất nhỏ gọn:

```java
@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

`main` vẫn là điểm vào bình thường của JVM. `SpringApplication.run(...)` là điểm bàn giao sang Boot: nó nhận primary source cùng command-line arguments và trả về một `ApplicationContext` đang chạy. Logic nghiệp vụ không nên bị nhét vào thao tác bootstrap này; nhiệm vụ của nó là thiết lập môi trường và container cho ứng dụng.

Vì vậy có thể xem `SpringApplication` là cầu nối giữa lúc tiến trình Java bắt đầu và Spring container. Các module sau sẽ đi sâu vòng đời, còn chương này chỉ giữ mô hình tư duy ở ranh giới đó.

### Tài liệu tham khảo

- [Spring Boot 3.3 — SpringApplication](https://docs.spring.io/spring-boot/3.3/reference/features/spring-application.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="main-to-context">SpringApplication.run biến main thành ApplicationContext như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

`SpringApplication.run(DemoApplication.class, args)` làm nhiều hơn việc tạo một instance của `DemoApplication`. Class được truyền vào là primary source để Boot và Spring bắt đầu khám phá cấu hình; `args` được đưa vào application arguments và cũng có thể tham gia cấu hình environment.

Ở mức tổng quan, lời gọi này tạo loại `ApplicationContext` phù hợp, nạp cấu hình vào context, refresh context để tạo các singleton bean rồi trả về context đang chạy. Loại context cụ thể phụ thuộc vào dạng ứng dụng và classpath.

Mối quan hệ quan trọng là:

```text
JVM gọi main(String[])
        ↓
main gọi SpringApplication.run(primarySource, args)
        ↓
Boot chuẩn bị và refresh một Spring ApplicationContext
        ↓
ứng dụng sẵn sàng thực hiện vai trò khi chạy
```

Vì `run` trả về context, một chương trình nhỏ có thể giữ reference nếu thật sự cần truy cập có kiểm soát:

```java
ConfigurableApplicationContext context =
        SpringApplication.run(DemoApplication.class, args);
```

Phần lớn ứng dụng không cần tự quản lý reference đó; ví dụ chỉ cho thấy kết quả là một Spring context thực sự, không phải runtime riêng bị ẩn của Boot.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bootstrap-flow">Quá trình khởi động diễn ra ở mức tổng quan ra sao?</a>

<details>
<summary>Xem chi tiết</summary>

Bootstrap dễ hiểu nhất khi xem như chuỗi trách nhiệm thay vì học thuộc toàn bộ sự kiện nội bộ. Spring Boot chuẩn bị application environment, xác định dạng ứng dụng ở mức lớn, tạo context tương ứng, nạp primary source, refresh context rồi hoàn tất startup.

```text
primary source + args
        ↓
chuẩn bị environment và application settings
        ↓
chọn/tạo ApplicationContext
        ↓
nạp configuration sources
        ↓
refresh context và tạo beans
        ↓
ứng dụng đang chạy
```

Hai yếu tố đã học trong module ảnh hưởng kết quả. **Classpath** cho Boot biết thư viện và khả năng nào đang có; **đầu vào cấu hình** cung cấp giá trị và lựa chọn tường minh. Auto-configuration có thể phản ứng với cả hai, nhưng mô hình condition chi tiết thuộc module `auto-configuration`.

Mức chi tiết này đủ cho câu hỏi debug đầu tiên lúc startup: JVM đã tới `main` chưa, Boot đã bắt đầu bootstrap chưa, và Spring context có refresh thành công hay không? Thứ tự event chi tiết để dành cho module runtime.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="high-level-lifecycle">Ứng dụng đi qua các giai đoạn khởi động, chạy và dừng có trật tự thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Ứng dụng Boot có một vòng đời cấp cao ngay cả trước khi bạn học toàn bộ lifecycle event. JVM khởi động tiến trình, `main` giao việc cho `SpringApplication`, context được chuẩn bị và refresh, rồi ứng dụng tiếp tục chạy theo dạng runtime cùng các công việc trên non-daemon thread đang hoạt động.

Với ứng dụng non-web kiểu command, công việc có thể hoàn thành và tiến trình có thể tự đi tới trạng thái kết thúc. Với web application, embedded server và các runtime thread của nó thường giữ tiến trình hoạt động để phục vụ request. Khi JVM shutdown theo luồng bình thường, shutdown hook mà Boot đăng ký sẽ đóng application context để các destruction callback do Spring quản lý có thể chạy.

```text
process start
   ↓
context start
   ↓
application running
   ↓
JVM shutdown có trật tự
   ↓
context close
```

Đây là **mô hình tư duy về vòng đời**, không phải toàn bộ event contract. Nó cung cấp đủ thuật ngữ để hiểu vì sao ứng dụng Boot không chỉ là một `main` chạy một lần, nhưng vẫn giữ các hook runtime chi tiết ở đúng module chuyên trách.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-handoff">Mô hình vòng đời trong Fundamentals dừng ở đâu?</a>

<details>
<summary>Xem chi tiết</summary>

Các chi tiết về runtime events, runners, availability states, task execution, virtual threads, logging integration và runtime services thuộc module `application-runtime`.

Điểm bàn giao này bao gồm các câu hỏi như "Boot event nào được publish tại thời điểm này?", "`ApplicationRunner` và `CommandLineRunner` chạy khi nào?", "liveness/readiness được mô hình hóa thế nào?" hay "Boot tích hợp task execution và logging ra sao?". Những câu hỏi đó xây trên mô hình start/run/stop đơn giản đã hình thành ở đây.

Fundamentals vẫn cần giúp bạn định vị lỗi ở mức thô: trước `SpringApplication`, trong lúc bootstrap/refresh context, hay sau khi ứng dụng đã chạy. Module runtime phía sau sẽ bổ sung thuật ngữ chi tiết hơn.

</details>

- [Quay lại đầu trang](#back-to-top)
