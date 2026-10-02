# ClassLoader

Module này giải thích cách JVM tìm, định nghĩa và liên kết class thông qua **class loading subsystem**, đồng thời làm rõ vì sao cùng một class name nhưng khác defining loader có thể tạo ra type identity khác nhau.

## Learning flow

1. class loading lifecycle;
2. built-in class loaders;
3. parent delegation;
4. custom class loader;
5. class identity;
6. context class loader;
7. resource loading;
8. initialization;
9. unloading và class-loader leak.

## Vì sao nên học?

ClassLoader là nền tảng phía sau plugin architecture, application server isolation, SPI/resource discovery và nhiều lỗi runtime khó hiểu. Module ưu tiên mental model về delegation, identity và lifetime thay vì chỉ học cách gọi `loadClass`.

## Phạm vi module

Reflection làm việc với class đã được load; JVM module khác chịu trách nhiệm execution/memory rộng hơn. Sau module này, learner nên giải thích được “class nào được loader nào định nghĩa, vì sao class cast có thể fail, và điều gì giữ một loader không được unload”.
