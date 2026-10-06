<a id="back-to-top"></a>

# Kết hợp Aspect, thứ tự và vòng đời

## Menu
- [Advice chain là một call stack lồng nhau](#ordering-mental-model)
- [Vòng đời Aspect instance: singleton, perthis, pertarget, pertypewithin](#aspect-instantiation-models)
- [Minh chứng thực thi cho Kết hợp Aspect, thứ tự và vòng đời](#ordering-demo)
- [Thứ tự Aspect không nên trở thành business protocol](#ordering-pitfall)
- [Tổng hợp Kết hợp Aspect, thứ tự và vòng đời](#ordering-conclusion)

Một join point có thể match nhiều Aspect cùng lúc.

## <a id="ordering-mental-model">Advice chain là một call stack lồng nhau</a>

<details>
<summary>Xem chi tiết</summary>

Khi nhiều advisor cùng match một method execution, Spring ghép chúng thành một interceptor chain. Với around advice, chain này hoạt động như các lời gọi method lồng nhau chứ không phải một danh sách lời gọi phẳng.

Giả sử hai aspect khai báo:

```text
Aspect A @Order(1)
Aspect B @Order(2)
```

Giá trị order nhỏ hơn có precedence cao hơn. Trên chiều đi vào invocation, aspect có precedence cao hơn chạy trước; trên chiều đi ra, nó hoàn tất sau cùng:

```text
A before
    B before
        target
    B after
A after
```

Thứ tự đảo ngược khi đi ra là hệ quả tự nhiên của call stack:

```text
A gọi proceed()
→ B gọi proceed()
→ target return
→ B tiếp tục
→ A tiếp tục
```

Cả `@Order` lẫn contract `Ordered` của Spring đều có thể biểu diễn precedence. Nếu hai aspect khác nhau không khai báo precedence explicit, không nên suy ra một thứ tự tương đối ổn định chỉ từ một lần chạy.

Còn một vấn đề ordering bên trong cùng aspect. Nếu nhiều advice **cùng loại** cùng match một join point, thứ tự khai báo method trong source code không phải precedence contract được đảm bảo. Khi correctness phụ thuộc thứ tự, nên tách concern thành các aspect riêng rồi đặt order explicit, hoặc gộp logic vào một advice method phù hợp.

### Tài liệu tham khảo

- Spring Framework Reference — Advice Ordering

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-instantiation-models">Vòng đời Aspect instance: singleton, perthis, pertarget, pertypewithin</a>

<details>
<summary>Xem chi tiết</summary>

Ordering trả lời **advice nào bao quanh advice nào**. Aspect instantiation trả lời một câu hỏi khác: **aspect instance nào giữ advice và trạng thái của aspect cho invocation hiện tại?**

Instantiation model mặc định của @AspectJ trong Spring là **singleton**:

```text
một aspect bean instance
→ được dùng lại cho các advised invocation phù hợp trong ApplicationContext đó
```

Đây là mô hình thông thường cho chính sách cắt ngang không giữ trạng thái theo từng invocation. Nếu singleton aspect lưu trường có thể thay đổi theo mỗi lời gọi, trường đó được chia sẻ giữa các lời gọi và có thể bị truy cập đồng thời. Nên giữ dữ liệu theo invocation trong biến cục bộ hoặc dùng trạng thái dùng chung an toàn cho concurrency thay vì nhét dữ liệu riêng của từng request vào trường của singleton aspect.

Spring còn hỗ trợ các @AspectJ per-clause model sau:

```text
perthis(pointcut)
→ liên kết một aspect instance riêng với mỗi this object phù hợp

pertarget(pointcut)
→ liên kết một aspect instance riêng với mỗi target object phù hợp

pertypewithin(TypePattern)
→ liên kết một aspect instance với mỗi target/application type phù hợp
```

Trong Spring AOP, `this` là **AOP proxy object**, còn `target` là target object nằm phía sau proxy. Vì vậy `perthis` và `pertarget` có thể dùng identity khác nhau dù đang advise cùng một method execution.

Per-clause chỉ quyết định cách liên kết aspect instance; nó không mở rộng join-point model của Spring AOP. Advice vẫn chỉ tham gia method execution đi qua Spring AOP proxy.

Các model rộng hơn của AspectJ là `percflow` và `percflowbelow` **không được Spring AOP hỗ trợ**. Chúng phụ thuộc vào control-flow join-point semantics mà proxy-based Spring AOP không cung cấp. Yêu cầu cần các model đó thuộc về full AspectJ weaving thay vì Spring AOP proxy.

Aspect khai báo bằng schema-based Spring AOP dùng singleton instantiation model. Các per-clause model thay thế ở trên thuộc @AspectJ style mà Spring hỗ trợ.

Aspect có trạng thái cần được thiết kế cẩn thận: lifecycle thay đổi phạm vi chia sẻ trạng thái, nhưng trạng thái của aspect không nên thay thế trạng thái nghiệp vụ hoặc trạng thái request/session vốn cần abstraction rõ ràng riêng.

### Tài liệu tham khảo

- Spring Framework Reference — Aspect Instantiation Models
- Spring Framework Reference — Schema-based AOP Support

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="ordering-demo">Minh chứng thực thi cho Kết hợp Aspect, thứ tự và vòng đời</a>

<details>
<summary>Xem chi tiết</summary>

Controller:

```text
OrderingController#observeOrdering()
```

Endpoint:

```text
GET /aop/ordering/observe
```

Target:

```text
OrderingService#execute()
```

Hai aspect cùng tham gia:

```text
OuterOrderingAspect @Order(1)
InnerOrderingAspect @Order(2)
```

Response `events`:

```text
order-1:before
order-2:before
target:ordering
order-2:after
order-1:after
```

Đây là bằng chứng trực tiếp cho model call stack lồng nhau. `OuterOrderingAspect` bắt đầu trước vì có precedence cao hơn rồi gọi `proceed()` để đi vào inner aspect. Sau khi target return, inner aspect hoàn tất trước khi quyền điều khiển quay lại outer aspect.

Demo chỉ thiết lập ordering cho hai aspect có order explicit này. Không nên mở rộng kết luận thành một cam kết về các advice khác không khai báo precedence.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="ordering-pitfall">Thứ tự Aspect không nên trở thành business protocol</a>

<details>
<summary>Xem chi tiết</summary>

Ordering explicit hữu ích khi nhiều chính sách cắt ngang có quan hệ precedence hợp lý. Ví dụ, một chính sách có thể cần thiết lập context trước khi chính sách khác quan sát invocation.

Nhưng một chain kiểu:

```text
Aspect A phải chạy trước B
B thay dữ liệu nghiệp vụ cho C
C phải chạy trước D
D quyết định use case có tiếp tục hay không
```

là dấu hiệu quy trình nghiệp vụ đang bị giấu trong advice ordering.

Thứ tự nghiệp vụ nên nằm trong abstraction của application/domain để bên gọi, input, output và luồng lỗi đều nhìn thấy rõ. Hãy dùng AOP ordering để compose chính sách cắt ngang, không dùng nó như một cơ chế điều phối quy trình ẩn.

Cũng cần phân biệt **thứ tự bắt buộc** và **thứ tự tình cờ quan sát được**. Chapter Pointcut có hai advice riêng dùng `this(...)` và `target(...)` cùng có thể match. Vì không khai báo precedence tương đối, experiment chỉ chứng minh cả hai đều match, không dạy rằng log nào bắt buộc phải xuất hiện trước.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="ordering-conclusion">Tổng hợp Kết hợp Aspect, thứ tự và vòng đời</a>

<details>
<summary>Xem chi tiết</summary>

Giữ riêng hai model composition:

```text
advisor ordering
→ quyết định các advice phù hợp lồng nhau thế nào trong invocation chain

aspect instantiation
→ quyết định aspect instance nào sở hữu advice/trạng thái
```

Với AOP trong ứng dụng thông thường, singleton aspect không giữ trạng thái theo invocation và chỉ đặt ordering explicit khi thật sự cần thường là model dễ suy luận nhất. Per-clause lifecycle là công cụ chuyên biệt, còn control-flow lifecycle như `percflow` vẫn nằm ngoài Spring AOP.

</details>

- [Quay lại đầu trang](#back-to-top)
