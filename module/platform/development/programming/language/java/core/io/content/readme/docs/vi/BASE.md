# Java I/O

Module này xây mental model cho **dòng dữ liệu và resource I/O** trong Java: phân biệt byte với character, stream với buffer/channel, filesystem path với open resource và ownership/lifecycle của resource.

## Learning flow

1. I/O mental model;
2. byte streams;
3. character streams;
4. buffered I/O;
5. legacy `File`;
6. `Path` và `Files`;
7. buffers và channels;
8. `FileChannel`;
9. resource management;
10. serialization;
11. chọn I/O abstraction phù hợp.

## Vì sao nên học?

I/O bug thường đến từ sai encoding, quên close resource, chọn abstraction không phù hợp hoặc nhầm file path với dữ liệu đã mở. Module ưu tiên hiểu boundary và lifecycle trước khi tối ưu.

Networking và advanced asynchronous/non-blocking runtime patterns thuộc module khác; phần này tập trung vào Java Core I/O foundation.
