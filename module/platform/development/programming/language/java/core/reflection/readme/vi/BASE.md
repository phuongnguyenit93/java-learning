# Reflection

Module này giải thích cách Java **inspect metadata và thao tác member ở runtime** thông qua Reflection API. Trọng tâm là hiểu capability, access boundary, type information còn giữ lại và chi phí/rủi ro của dynamic access.

## Learning flow

1. Reflection mental model;
2. class metadata;
3. fields;
4. methods;
5. constructors;
6. dynamic invocation;
7. access control;
8. generic type inspection;
9. dynamic proxy;
10. limitations và risks.

## Vì sao nên học?

Reflection là nền tảng của nhiều framework, serializer, mapper và test/tooling infrastructure, nhưng dùng quá mức làm code khó kiểm tra tĩnh và khó refactor. Module hướng tới việc biết khi nào metadata-driven behavior là hợp lý và khi nào direct code/API rõ hơn.

ClassLoader chịu trách nhiệm đưa class vào runtime; Annotation cung cấp metadata; Dynamic Runtime/`java.lang.invoke` là một runtime mechanism khác với mục tiêu và contract khác Reflection.
