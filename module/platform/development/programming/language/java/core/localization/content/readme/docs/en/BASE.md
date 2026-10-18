# Localization

This module explains how Java represents **locale-sensitive behavior**: language/region identity, resource lookup, message formatting, number/currency/date-time presentation, collation, and text boundaries.

## Learning flow

1. the localization mental model;
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

## Why learn this module?

Localization is more than translating strings. The same data may need different formatting, sorting, message selection, and fallback behavior depending on locale. Business values and presentation locale should remain distinct.

The Date-Time module owns temporal semantics, while String owns the Unicode/text foundation. This module focuses on locale-driven presentation and resource selection.
