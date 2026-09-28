<a id="back-to-top"></a>

# Multiple Aspects và Ordering

## Menu
- [1. Advice chain là nested call stack](#ordering-mental-model)
- [2. Demo trong module](#ordering-demo)
- [3. Ordering không nên trở thành business protocol](#ordering-pitfall)
- [4. Kết luận](#ordering-conclusion)

Một join point có thể match nhiều Aspect cùng lúc.

## <a id="ordering-mental-model">1. Advice chain là nested call stack</a>

<details>
<summary>Click for details</summary>

Giả sử hai `@Around` Aspect cùng match target:

```text
Aspect A @Order(1)
Aspect B @Order(2)
```

Với precedence hiện tại, Aspect có order nhỏ hơn đứng ngoài call chain:

```text
A before
    B before
        target
    B after
A after
```

Điểm dễ nhầm là chiều đi ra đảo ngược chiều đi vào vì đây là nested invocation, không phải bốn callback độc lập chạy tuyến tính.

`@Order`/precedence phải rõ nếu correctness phụ thuộc thứ tự. Nếu hai advice cùng match nhưng không có precedence contract rõ ràng, **không nên lấy thứ tự log quan sát được trong một lần chạy làm guarantee**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="ordering-demo">2. Demo trong module</a>

<details>
<summary>Click for details</summary>

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

Hai Aspect:

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

Đây là evidence trực tiếp cho mental model nested.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="ordering-pitfall">3. Ordering không nên trở thành business protocol</a>

<details>
<summary>Click for details</summary>

`@Order` hữu ích khi nhiều cross-cutting concern cần precedence rõ ràng, nhưng dependency phức tạp kiểu:

```text
Aspect A phải chạy trước B,
B phải thay dữ liệu cho C,
C phải chạy trước D
```

là dấu hiệu design đang khó hiểu.

Business rule nên nằm trong abstraction/service rõ ràng thay vì biến advice ordering thành workflow engine ngầm.

Chapter Pointcut cố ý có hai advice `this(...)` và `target(...)` cùng match mà không dùng thứ tự của chúng làm learning contract. Đây chính là ví dụ cho nguyên tắc này.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="ordering-conclusion">4. Kết luận</a>

<details>
<summary>Click for details</summary>

Khi đọc log của nhiều `@Around` Aspect, hãy hình dung stack lồng nhau thay vì danh sách callback phẳng.

</details>

- [Quay lại đầu trang](#back-to-top)
