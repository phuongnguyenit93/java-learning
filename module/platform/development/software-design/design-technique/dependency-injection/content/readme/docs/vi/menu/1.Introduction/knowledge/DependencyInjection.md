# Inversion of Control và Dependency Injection

## <a id="ioc-di-what">1. IoC và DI là gì?</a>

**Inversion of Control (IoC)** là nguyên tắc đảo quyền quyết định về việc tạo, chọn hoặc điều phối dependency ra khỏi business component.

**Dependency Injection (DI)** là một kỹ thuật phổ biến để hiện thực điều đó: dependency được cung cấp từ bên ngoài thay vì component tự tạo dependency cụ thể.

## <a id="ioc-di-why">2. Tại sao cần chúng?</a>

Khi một class tự `new` mọi dependency cụ thể, creation policy và business behavior bị dính vào cùng một nơi.

Điều này làm việc thay implementation, test isolation và composition ở application boundary khó hơn.

## <a id="ioc-di-before">3. Cách đơn giản hơn là gì?</a>

Một object hoàn toàn có thể tự tạo dependency nếu dependency nhỏ, ổn định và thực sự là implementation detail.

DI trở nên hữu ích khi dependency là collaborator có contract riêng, lifecycle riêng hoặc cần thay thế theo environment/test.

## <a id="ioc-di-solution">4. DI giải quyết thế nào?</a>

```text
composition boundary
      ↓ inject
business component
      ↓ uses
dependency contract
```

Creation và wiring được tách khỏi code sử dụng dependency.

## <a id="ioc-di-framework">5. DI không đồng nghĩa với Spring</a>

Constructor injection hoặc manual composition vẫn là DI dù không có container.

Spring DI là một implementation/framework mechanism cụ thể và thuộc Spring modules, không phải canonical owner của concept DI.
