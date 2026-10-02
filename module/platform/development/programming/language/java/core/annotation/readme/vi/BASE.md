# Annotation

Module này giải thích annotation như **metadata có cấu trúc gắn vào Java program elements**, từ annotation có sẵn đến custom annotation và cách metadata đó được giữ lại, giới hạn target và được tool/runtime sử dụng.

## Vì sao nên học?

Annotation xuất hiện xuyên suốt Java ecosystem, nhưng annotation tự nó không “thực thi logic”. Cần hiểu lifecycle và consumer của metadata để tránh nhầm giữa declaration, reflection/runtime processing và compile-time annotation processing.

## Learning flow

1. mental model và cú pháp annotation;
2. built-in annotations;
3. định nghĩa custom annotation;
4. retention policy;
5. target;
6. meta-annotations;
7. repeatable và inherited annotations;
8. annotation processing;
9. tổng hợp design guideline cho annotation contract.

## Phạm vi module

Module sở hữu annotation language model và metadata contract. Reflection là nơi đọc metadata ở runtime; compile-time tooling có thể dùng annotation processor. Mục tiêu cuối cùng là biết annotation nào tồn tại ở phase nào và ai thực sự tiêu thụ nó.
