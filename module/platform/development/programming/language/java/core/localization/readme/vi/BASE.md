# Localization

Module này giải thích cách Java biểu diễn **locale-sensitive behavior**: language/region identity, resource lookup, message formatting, number/currency/date-time presentation, collation và text boundary.

## Learning flow

1. localization mental model;
2. `Locale`;
3. language tags;
4. `ResourceBundle`;
5. `MessageFormat`;
6. number formatting;
7. currency;
8. collation;
9. date-time localization;
10. text segmentation;
11. bidirectional text;
12. fallback;
13. localization pitfalls.

## Vì sao nên học?

Localization không chỉ là “dịch chuỗi”. Cùng một dữ liệu có thể cần format, sort, plural/message choice và fallback khác nhau theo locale. Business value và presentation locale phải được tách rõ.

Date-Time module sở hữu temporal semantics; String module sở hữu Unicode/text foundation. Module này tập trung vào cách trình bày và lựa chọn tài nguyên theo locale.
