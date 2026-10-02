# String và Text

Module này xây mental model cho **text trong Java**, từ immutability và String Pool đến Unicode/code point, encoding, regex và text blocks.

## Learning flow

1. `String` immutability;
2. String Pool;
3. equality;
4. core String operations;
5. concatenation;
6. `StringBuilder`;
7. `StringBuffer`;
8. `intern`;
9. Unicode và code point;
10. encoding;
11. regex;
12. text blocks.

## Mental model cần giữ

`String` là immutable sequence abstraction, nhưng “character” trong business text không phải lúc nào cũng tương đương một Java `char`. Unicode representation và byte encoding là hai layer khác nhau; lỗi text thường xuất hiện khi hai layer này bị trộn lẫn.

Localization xử lý locale-sensitive presentation; I/O xử lý boundary byte/character khi đọc ghi dữ liệu. Module này cung cấp text foundation cho cả hai.
