# Reactive Programming

## <a id="reactive-what">1. Reactive Programming là gì?</a>

Reactive Programming là paradigm tổ chức logic quanh **dòng dữ liệu/sự kiện thay đổi theo thời gian** và sự lan truyền của thay đổi qua các thành phần xử lý.

Thay vì luôn hỏi “hãy lấy cho tôi giá trị ngay bây giờ”, code reactive thường mô tả “khi có dữ liệu hoặc sự kiện mới, hãy xử lý nó theo pipeline này”.

## <a id="reactive-why">2. Tại sao nó tồn tại?</a>

Các hệ thống có event liên tục, I/O bất đồng bộ hoặc producer/consumer chạy với tốc độ khác nhau cần một model biểu diễn data flow và timing rõ ràng.

Callback thủ công có thể giải quyết từng case nhưng khi flow dài, composition, error propagation và cancellation dễ trở nên rối.

## <a id="reactive-solution">3. Reactive Programming giải quyết thế nào?</a>

```text
source
  ↓
stream of signals
  ↓
transform / filter / combine
  ↓
consumer
```

Các concern quan trọng gồm push/pull model, asynchronous boundary, backpressure, cancellation và error signaling.

## <a id="reactive-streams-boundary">4. Reactive Streams nằm ở đâu?</a>

Reactive Streams không phải toàn bộ Reactive Programming. Nó là một specification/contract tập trung vào asynchronous stream processing với non-blocking backpressure.

Publisher, Subscriber, Subscription và Processor thuộc layer contract đó.

## <a id="reactive-framework-boundary">5. Boundary với framework</a>

Module này không sở hữu Spring WebFlux hay framework API cụ thể.

Spring-specific reactive implementation thuộc `framework/spring-framework/reactive`.
