# Date và Time

Module này xây mental model cho Java Date-Time API bằng cách tách rõ **local date/time**, **instant trên timeline**, **offset/time zone**, **amount/duration** và **format/parse**.

## Learning flow

1. mental model date-time;
2. `LocalDate`;
3. `LocalTime`;
4. `LocalDateTime`;
5. `Instant`;
6. `ZoneOffset` và `ZoneId`;
7. `ZonedDateTime` / `OffsetDateTime`;
8. `Duration` và `Period`;
9. formatting/parsing;
10. arithmetic/comparison;
11. `Clock`;
12. legacy interop và pitfalls.

## Vì sao nên học?

Phần lớn bug thời gian đến từ việc dùng sai loại temporal object hoặc đánh đồng local wall-clock time với một thời điểm tuyệt đối. Mục tiêu là chọn đúng representation trước khi nghĩ đến format hiển thị.

## Kết quả mong đợi

Learner nên mô hình hóa được requirement theo timeline/local/zone, thực hiện arithmetic đúng ngữ nghĩa và biết khi nào cần boundary với localization hoặc legacy `Date/Calendar`.
