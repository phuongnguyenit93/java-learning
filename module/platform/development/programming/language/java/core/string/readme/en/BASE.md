# Strings and Text

This module builds the mental model for **text in Java**, from immutability and the String Pool to Unicode/code points, encodings, regular expressions, and text blocks.

## Learning flow

1. `String` immutability;
2. the String Pool;
3. equality;
4. core String operations;
5. concatenation;
6. `StringBuilder`;
7. `StringBuffer`;
8. `intern`;
9. Unicode and code points;
10. encoding;
11. regular expressions;
12. text blocks.

## Mental model to retain

`String` is an immutable sequence abstraction, but a user-perceived character is not always one Java `char`. Unicode representation and byte encoding are separate layers, and text bugs often appear when those layers are mixed.

Localization owns locale-sensitive presentation; I/O owns byte/character boundaries when reading and writing data. This module provides the text foundation for both.
