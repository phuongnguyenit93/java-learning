<a id="back-to-top"></a>

# Chuyển đổi kiểu trong Spring

## Menu
- [ConversionService là điểm vào khi chạy Conversion](#conversion-service-role)
- [ConverterRegistry và đăng ký Converter](#converter-registry)
- [Converter cho một cặp kiểu nguồn và đích](#converter-contract)
- [ConverterFactory cho một họ kiểu đích](#converter-factory-contract)
- [GenericConverter và Conditional Conversion](#generic-conditional-conversion)
- [TypeDescriptor và ngữ cảnh Conversion](#type-descriptor-context)
- [Chọn Converter và mô hình lỗi Conversion](#conversion-failure-model)
- [Ranh giới với PropertyEditor legacy](#property-editor-boundary)

## <a id="conversion-service-role">ConversionService là điểm vào khi chạy Conversion</a>

<details>
<summary>Xem chi tiết</summary>

`ConversionService` là điểm vào dành cho phía sử dụng hệ thống conversion của Spring. Bên gọi hỏi một source type có convert được sang target type hay không rồi yêu cầu conversion; bên gọi không cần biết cách triển khai converter cụ thể nào thực hiện công việc.

```java
ConversionService service = DefaultConversionService.getSharedInstance();

boolean supported = service.canConvert(String.class, Integer.class);
Integer quantity = service.convert("42", Integer.class);
```

Lớp gián tiếp này quan trọng vì code binding chỉ cần phụ thuộc một service ổn định trong khi việc đăng ký converter có thể thay đổi độc lập. Cùng một service có thể chọn built-in converter, converter của ứng dụng, factory hoặc generic/conditional converter dựa trên source và target descriptor.

Các overload giàu thông tin hơn nhận `TypeDescriptor`. Những overload này giữ được ngữ cảnh mà `Class<?>` thô không biểu diễn được, như element type của collection, key/value type của map, annotation và vị trí property. Ngữ cảnh đó đặc biệt quan trọng khi convert collection hoặc khi conversion phụ thuộc metadata.

`canConvert(...)` chỉ là phép hỏi khả năng hỗ trợ, không phải lời đảm bảo rằng mọi value cụ thể sau này đều convert thành công. Với collection, array và map, Spring có thể xác định được đường chuyển đổi về mặt cấu trúc nhưng một element cụ thể vẫn có thể lỗi và tạo `ConversionException`.

Nên xem `ConversionService` là hạ tầng ứng dụng: cấu hình converter tái sử dụng một lần, sau đó phía sử dụng chỉ yêu cầu conversion. Tránh rải `Integer.parseInt`, date parsing hoặc việc tạo value object vào code binding nếu phép biến đổi đó thực chất là chính sách conversion dùng chung của ứng dụng.

### Tài liệu tham khảo

- [Spring Framework 6.1.14 `ConversionService`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/core/convert/ConversionService.html)
- [Spring Framework 6.1.14 `DefaultConversionService`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/core/convert/support/DefaultConversionService.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="converter-registry">ConverterRegistry và đăng ký Converter</a>

<details>
<summary>Xem chi tiết</summary>

`ConversionService` mô tả phía *sử dụng* conversion; `ConverterRegistry` mô tả phía *cấu hình* hệ thống conversion. Tách hai vai trò giúp component ứng dụng không tự ý sửa chính sách conversion chỉ vì nó cần convert một value.

Registry chấp nhận nhiều dạng converter:

```java
DefaultConversionService service = new DefaultConversionService();

service.addConverter(new StringToOrderIdConverter());
service.addConverterFactory(new StringToCodeEnumFactory());
service.addConverter(new EntityReferenceConverter());
```

Khi đăng ký `Converter<S,T>` hoặc `ConverterFactory<S,R>` đơn giản, Spring thường suy ra cặp kiểu nguồn/đích từ tham số generic. Registry cũng có overload nhận trực tiếp `Class` nguồn và đích, hữu ích khi chủ đích tái sử dụng cùng một converter cho nhiều cặp khác nhau.

Thứ tự đăng ký không nên trở thành quy tắc nghiệp vụ ngầm. Nên thiết kế converter sao cho phạm vi áp dụng rõ ràng. Khi nhiều ứng viên có thể cùng khớp về mặt khái niệm, conditional conversion hoặc cặp kiểu nguồn/đích hẹp hơn giúp việc lựa chọn dựa trên metadata tường minh thay vì giả định khó kiểm soát.

Đăng ký tập trung cũng tạo một nơi để rà soát chính sách conversion dùng toàn cục. Converter đăng ký quá rộng có thể ảnh hưởng binding và các thành phần khác của Framework dùng cùng service, nên converter cần cho kết quả xác định, thread-safe theo hợp đồng và không giữ trạng thái có thể thay đổi riêng cho từng yêu cầu.

### Tài liệu tham khảo

- [Spring Framework 6.1.14 `ConverterRegistry`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/core/convert/converter/ConverterRegistry.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="converter-contract">Converter cho một cặp kiểu nguồn và đích</a>

<details>
<summary>Xem chi tiết</summary>

Dùng `Converter<S,T>` khi một source type có một phép biến đổi rõ ràng sang một target type. Đây là conversion SPI đơn giản nhất và cũng là functional interface.

```java
public record OrderId(long value) {}

public final class StringToOrderIdConverter
        implements Converter<String, OrderId> {

    @Override
    public OrderId convert(String source) {
        long value = Long.parseLong(source.trim());
        if (value <= 0) {
            throw new IllegalArgumentException("Order id must be positive");
        }
        return new OrderId(value);
    }
}
```

Hợp đồng `Converter` của Spring bảo đảm `source` truyền vào `convert` là non-null. Converter được phép trả `null`, nhưng hành vi đó cần có ý nghĩa rõ ràng và không nên dùng để che dữ liệu đầu vào không hợp lệ.

Converter được đăng ký trong hệ thống conversion được kỳ vọng thread-safe và có thể dùng chung. Không lưu trạng thái có thể thay đổi của từng lần conversion trong instance field; hãy xử lý từ method argument rồi trả về kết quả.

Khi non-null source không thể convert theo hợp đồng, có thể throw `IllegalArgumentException`. Nếu conversion được gọi từ binder, lỗi này có thể được biểu diễn thành binding/conversion error ở tầng điều phối phía trên.

Chọn converter đơn giản khi phép biến đổi ổn định và không phụ thuộc ngữ cảnh. Nếu thao tác cần biết target subtype, field annotation, generic element type hoặc điều kiện khớp bổ sung, SPI khác sẽ phù hợp hơn.

### Tài liệu tham khảo

- [Spring Framework 6.1.14 `Converter`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/core/convert/converter/Converter.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="converter-factory-contract">ConverterFactory cho một họ kiểu đích</a>

<details>
<summary>Xem chi tiết</summary>

`ConverterFactory<S,R>` hữu ích khi một dạng biểu diễn nguồn có thể convert sang nhiều target subtype cùng họ. Thay vì đăng ký một converter class cho từng subtype, factory nhận target `Class<T>` được yêu cầu rồi trả về `Converter<S,T>` phù hợp.

Mô hình tư duy điển hình là "một source format, một target family":

```java
public final class StringToEnumFactory
        implements ConverterFactory<String, Enum> {

    @Override
    public <T extends Enum> Converter<String, T> getConverter(Class<T> targetType) {
        return source -> Enum.valueOf(targetType, source.trim());
    }
}
```

Nếu target là `Priority.class`, factory tạo `String -> Priority`; nếu target là `Status.class`, nó tạo `String -> Status`. Kiểu đích cơ sở `R` thể hiện phạm vi mà factory hỗ trợ.

Factory phù hợp hơn `GenericConverter` khi thông tin thay đổi duy nhất là target subtype và thuật toán vẫn đồng nhất. API giữ được strong typing và cách triển khai dễ test hơn.

Không nên dùng converter factory để gom các target type không liên quan dưới `Object` chỉ nhằm giảm số class. Target family nên có kiểu cơ sở chung có ý nghĩa và chính sách conversion nhất quán. Nếu quyết định phụ thuộc annotation, generic metadata hoặc ngữ cảnh descriptor khác, conditional/generic converter phù hợp hơn.

### Tài liệu tham khảo

- [Spring Framework 6.1.14 `ConverterFactory`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/core/convert/converter/ConverterFactory.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-conditional-conversion">GenericConverter và Conditional Conversion</a>

<details>
<summary>Xem chi tiết</summary>

`GenericConverter` là conversion SPI linh hoạt nhất. Nó có thể khai báo nhiều source/target pair và method `convert` nhận cả source/target `TypeDescriptor`. Chính ngữ cảnh bổ sung này là lý do dùng nó; nếu không cần ngữ cảnh, `Converter` đơn giản hơn và dễ hiểu hơn.

```java
public final class EntityReferenceConverter
        implements ConditionalGenericConverter {

    @Override
    public Set<ConvertiblePair> getConvertibleTypes() {
        return null; // Xét mọi pair; matches(...) thu hẹp phạm vi thực tế.
    }

    @Override
    public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
        return sourceType.getType() == String.class
                && targetType.hasAnnotation(EntityRef.class);
    }

    @Override
    public Object convert(Object source,
                          TypeDescriptor sourceType,
                          TypeDescriptor targetType) {
        // Resolve hoặc tạo reference riêng của ứng dụng.
        return resolveReference((String) source, targetType.getType());
    }
}
```

`ConditionalGenericConverter` kết hợp generic converter với điều kiện `matches(...)`. Conversion service có thể xét source/target pair trước, sau đó để converter quyết định ngữ cảnh descriptor hiện tại có làm nó áp dụng được hay không. Nhiều converter nội bộ của Spring dùng cách tiếp cận này cho conversion phụ thuộc metadata.

Khác `Converter`, `GenericConverter` có thể nhận source `null`, nên cách triển khai cần có chính sách xử lý `null` rõ khi trường hợp đó có ý nghĩa.

Không nên chọn SPI này chỉ vì nó mạnh. Convertible pair quá rộng cộng với matching rule lỏng khiến hệ thống conversion dùng toàn cục khó suy luận. Luôn ưu tiên SPI hẹp nhất biểu diễn đúng quyết định thực tế.

### Tài liệu tham khảo

- [Spring Framework 6.1.14 `GenericConverter`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/core/convert/converter/GenericConverter.html)
- [Spring Framework 6.1.14 `ConditionalGenericConverter`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/core/convert/converter/ConditionalGenericConverter.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-descriptor-context">TypeDescriptor và ngữ cảnh Conversion</a>

<details>
<summary>Xem chi tiết</summary>

`Class<?>` chỉ cho biết raw runtime type, trong khi conversion thường cần nhiều ngữ cảnh hơn. `TypeDescriptor` mang theo type cùng thông tin vị trí và nested type. Nó có thể mô tả field, property hoặc method parameter; cho phép đọc annotation; và mô tả element type của collection hay key/value type của map.

Xét hai field có cùng raw type:

```java
class PriceForm {
    @CurrencyCode("USD")
    BigDecimal retailPrice;

    @CurrencyCode("EUR")
    BigDecimal wholesalePrice;
}
```

Cả hai đều là `BigDecimal.class`, nên raw class không thể giải thích vì sao cách hiểu text có thể khác. `TypeDescriptor` giữ annotation của field để conditional converter hoặc formatter ra quyết định theo ngữ cảnh.

Nó cũng quan trọng với container. `List<Integer>` và `List<UUID>` đều erase thành `List.class`, nhưng element descriptor thể hiện yêu cầu conversion khác nhau. Spring có thể dùng nested metadata đó khi convert từng phần tử.

```java
TypeDescriptor source = TypeDescriptor.valueOf(String.class);
TypeDescriptor target = TypeDescriptor.collection(
        List.class,
        TypeDescriptor.valueOf(Integer.class)
);

Object converted = conversionService.convert(
        List.of("1", "2", "3"),
        TypeDescriptor.collection(List.class, source),
        target
);
```

Quy tắc thực tế: class-based conversion đủ cho phép biến đổi scalar đơn giản; descriptor-aware conversion cần thiết khi generic, annotation hoặc vị trí property là một phần của ngữ nghĩa chuyển đổi.

### Tài liệu tham khảo

- [Spring Framework 6.1.14 `TypeDescriptor`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/core/convert/TypeDescriptor.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="conversion-failure-model">Chọn Converter và mô hình lỗi Conversion</a>

<details>
<summary>Xem chi tiết</summary>

Việc chọn converter có hai giai đoạn: trước hết Spring phải tìm được conversion path đủ điều kiện; sau đó converter được chọn phải xử lý thành công value cụ thể.

Nếu không có converter cho source/target pair được yêu cầu, conversion lỗi vì hệ thống không có chiến lược biến đổi. Nếu converter tồn tại nhưng từ chối value cụ thể, lỗi xảy ra trong lúc convert. Cả hai khác validation: conversion hỏi có tạo được target value có kiểu hay không, validation hỏi value đó có được ứng dụng chấp nhận hay không.

```java
try {
    UUID id = conversionService.convert(rawId, UUID.class);
    // dùng typed id
}
catch (ConversionException ex) {
    // Source không convert được bằng hệ thống conversion hiện tại.
}
```

`canConvert(...)` hữu ích khi conversion là tùy chọn hoặc code cần chọn chiến lược dự phòng, nhưng không cần gọi nó như bước kiểm tra trước mọi `convert`. Với nhiều trường hợp, thao tác convert mới là nguồn sự thật cuối cùng. Đặc biệt với collection/map, `canConvert` có thể là true nhưng một element sau đó vẫn tạo `ConversionException`.

Lỗi thiết kế phổ biến gồm đăng ký converter quá rộng, parse dữ liệu phụ thuộc locale trong converter không có ngữ cảnh và nuốt dữ liệu đầu vào sai định dạng bằng cách trả giá trị mặc định tùy tiện. Converter nên giữ rõ sự khác nhau giữa "không có value", "value không hợp lệ" và domain value hợp lệ.

Khi binder gọi conversion, lỗi property-access/conversion có thể được chuyển thành binding error có cấu trúc. Chi tiết điều phối đó thuộc chương DataBinder; trách nhiệm của conversion layer là tạo target value hoặc báo lỗi rõ ràng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="property-editor-boundary">Ranh giới với PropertyEditor legacy</a>

<details>
<summary>Xem chi tiết</summary>

Spring tồn tại trước `ConversionService`, vì vậy Framework vẫn hỗ trợ JavaBeans `PropertyEditor`. Property editor là object có trạng thái, có thể chuyển text thành property value và ngược lại. Chúng vẫn quan trọng cho khả năng tương thích và một số điểm mở rộng binding cấp thấp, nhưng không phải lựa chọn ưu tiên cho chính sách conversion tổng quát mới.

Khác biệt lớn nằm ở phạm vi và trạng thái. Spring `Converter` là chiến lược có thể dùng chung và được kỳ vọng thread-safe khi đăng ký tập trung. `PropertyEditor` truyền thống giữ trạng thái chỉnh sửa có thể thay đổi như giá trị hiện tại, nên từng editor phải được quản lý đúng phạm vi thay vì coi như converter toàn cục không trạng thái.

`DataBinder` vẫn có thể đăng ký custom editor:

```java
DataBinder binder = new DataBinder(target);
binder.registerCustomEditor(
        Date.class,
        new CustomDateEditor(new SimpleDateFormat("yyyy-MM-dd"), true)
);
```

Với conversion ở cấp ứng dụng hiện đại, nên ưu tiên `ConversionService` cùng converter/formatter. Tuy vậy vẫn cần hiểu `PropertyEditor` vì Spring API và legacy integration còn expose cơ chế này, đồng thời nó giải thích một số binding extension point.

Không nên trộn hai cơ chế mà không có lý do. Nếu một khái niệm của ứng dụng vừa có `Converter` dùng toàn cục, vừa có custom editor riêng cho field nhưng ngữ nghĩa khác nhau, chính sách thực tế sẽ khó dự đoán và rà soát. Hãy dùng cơ chế hẹp nhất đúng với phạm vi mong muốn.

</details>

- [Quay lại đầu trang](#back-to-top)
