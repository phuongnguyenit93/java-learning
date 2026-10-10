<a id="back-to-top"></a>

# Cơ chế ghép hành vi và ranh giới các mô hình AOP

## Menu
- [Ghép aspect tại thời điểm biên dịch](#aop-compile-time)
- [Ghép aspect tại thời điểm nạp lớp](#aop-load-time)
- [Chặn lời gọi qua lớp trung gian khi chạy](#aop-runtime)
- [Phạm vi quan sát và giới hạn của từng mô hình ghép](#aop-model-boundary)
- [AspectJ weaving và Spring AOP proxy: Hai phạm vi join point khác nhau](#aop-aspectj-spring-boundary)
- [Giới hạn interception và lời gọi nội bộ trong mô hình proxy](#aop-interception-limits)

## <a id="aop-compile-time">Ghép aspect tại thời điểm biên dịch</a>

<details>
<summary>Xem chi tiết</summary>

Ghép **tại thời điểm biên dịch** đưa hành vi bổ sung vào bytecode đầu ra trước khi ứng dụng chạy. Với AspectJ, trình biên dịch/bộ ghép có thể xử lý các join point mà không cần lời gọi phải đi qua proxy bên ngoài, chẳng hạn một số thao tác trên trường dữ liệu.

Đổi lại, quy trình build cần tích hợp kỹ thuật ghép phù hợp; việc thấy mã nguồn target không gọi logger không có nghĩa bytecode chạy không chứa logic đó. Đây là câu chuyện **đường triển khai**, không phải một lý do tự thân để chọn AOP cho mọi concern.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-load-time">Ghép aspect tại thời điểm nạp lớp</a>

<details>
<summary>Xem chi tiết</summary>

**Ghép khi nạp lớp (load-time weaving)** biến đổi bytecode tại thời điểm lớp được nạp, muộn hơn biên dịch nhưng trước khi lớp thực thi bình thường. Đây là việc điều chỉnh mã lớp, không phải cách một proxy chặn lời gọi tới một đối tượng đã tạo.

Khi gỡ lỗi phải xác nhận bộ nạp lớp có thực sự áp dụng transformer cho lớp mục tiêu và thời điểm nào lớp đã được nạp. Khái niệm ở đây chỉ để phân biệt thời điểm ghép; cấu hình agent/class loader cụ thể nằm trong tài liệu AspectJ, không thuộc bài paradigm.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-runtime">Chặn lời gọi qua lớp trung gian khi chạy</a>

<details>
<summary>Xem chi tiết</summary>

Một số implementation áp dụng cross-cutting behavior tại runtime thông qua một lớp trung gian hoặc interception mechanism.

Mental model:

```text
caller
  ↓
interception boundary
  ↓
cross-cutting behavior
  ↓
target
```

Với **proxy/interception**, caller gọi tới ranh giới trung gian; proxy quyết định chạy chuỗi advice rồi mới chuyển sang target. Ví dụ `external → timingProxy → transferService.transfer()`. Nếu caller đi vòng qua proxy, advice của proxy đó không thấy lời gọi.

Ưu điểm là có thể tổ chức ở runtime mà không cần ghép bytecode của target; giới hạn là chỉ chặn được những lời gọi mà hình thức proxy hỗ trợ. Khi so với compile/load-time weaving, hãy so **đường lời gọi thực** chứ không suy từ hai annotation trông giống nhau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-model-boundary">Phạm vi quan sát và giới hạn của từng mô hình ghép</a>

<details>
<summary>Xem chi tiết</summary>

Mô hình ghép quyết định **những loại join point mà aspect có thể quan sát**, nên ranh giới áp dụng advice khác nhau giữa các công nghệ. Chặn lời gọi lúc chạy có thể chỉ nhìn thấy một số phương thức đi qua proxy; ghép bytecode có thể hỗ trợ cả việc đọc hoặc gán trường dữ liệu, tùy mô hình AspectJ. Pointcut chính xác đến đâu cũng không thể biến một thao tác trên trường thành lần thực thi phương thức mà Spring AOP hỗ trợ.

Hãy lập bảng kiểm cho một dự án: cơ chế ghép, tập đối tượng đủ điều kiện, loại join point hỗ trợ, các đường gọi đi qua/đi vòng ranh giới, và khả năng quan sát lỗi. Chỉ sau đó mới kết luận chính sách đang được áp dụng ở phạm vi mong muốn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-aspectj-spring-boundary">AspectJ weaving và Spring AOP proxy: Hai phạm vi join point khác nhau</a>

<details>
<summary>Xem chi tiết</summary>

**AspectJ weaving** có thể tác động vào bytecode và hỗ trợ một mô hình join point rộng hơn, bao gồm thực thi phương thức, lời gọi phương thức, constructor và một số truy cập trường theo ngôn ngữ AspectJ. **Spring AOP** thông thường dùng proxy cho **method execution trên Spring bean**; không chặn truy cập trường tùy ý. Dùng cú pháp `@Aspect` kiểu AspectJ bên trong Spring **không có nghĩa** ứng dụng đang dùng AspectJ weaver.

Ví dụ một thao tác `balance = balance - 100` nội bộ không được Spring AOP nhận diện như field-set join point; cần dùng mô hình triển khai thích hợp hoặc thiết kế lại ranh giới hành vi. Đây là **giới hạn năng lực**, không phải bug pointcut. Đối chiếu [Spring AOP Capabilities](https://docs.spring.io/spring-framework/reference/core/aop/introduction-spring-defn.html) và [AspectJ Programming Guide](https://eclipse.dev/aspectj/doc/latest/progguide/progguide.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-interception-limits">Giới hạn interception và lời gọi nội bộ trong mô hình proxy</a>

<details>
<summary>Xem chi tiết</summary>

Giả sử caller ngoài gọi `proxy.execute()`; bên trong target `execute()` lại gọi `this.verify()`. Lời gọi sau đi trực tiếp từ đối tượng target tới chính nó, **không quay lại proxy**, nên advice của Spring AOP dành cho `verify()` không chạy nhờ lời gọi đó. Ngược lại, một lời gọi `proxy.verify()` đi từ bên ngoài có thể được chặn nếu phương thức đủ điều kiện.

Ngoài self-invocation, loại proxy và phạm vi phương thức có thể ảnh hưởng việc interception xảy ra; interface-based proxy và class-based proxy có ranh giới khác nhau. Không đồng nhất chuyện này với AspectJ bytecode weaving: weaving không gặp vấn đề **đi vòng proxy** theo cách trên. Xem [Spring Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html).

</details>

- [Quay lại đầu trang](#back-to-top)
