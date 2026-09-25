# Encoding

## <a id="text-vs-bytes">Mental model text và bytes</a>
Java String là sequence UTF-16 code unit; file, network payload và nhiều storage format là bytes. Charset định nghĩa mapping giữa text character/code point và byte sequence. Không được assume bytes “đã là text”.

## <a id="charset-encode-decode">Encode/decode bằng Charset</a>
Encoding chuyển text thành bytes với charset đã chọn; decoding chuyển bytes thành text bằng charset. Boundary nên dùng charset explicit như `StandardCharsets.UTF_8`.

```java
byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
String copy = new String(bytes, StandardCharsets.UTF_8);
```

## <a id="default-charset-risk">Rủi ro platform default charset</a>
API dùng default charset có thể behavior khác giữa machine/process configuration. Dù Java hiện đại đã thay đổi default policy, dựa vào “máy đang dùng gì” vẫn là external-data contract yếu. Persist/network content nên declare charset explicit.

## <a id="malformed-input">Boundary malformed/unmappable input</a>
Decoder có thể gặp malformed byte sequence; encoder có thể gặp character không represent được trong target charset. Convenience method thường replace invalid data, còn `CharsetEncoder`/`CharsetDecoder` cho configure report/replace/ignore. Data integrity quan trọng thì failure behavior phải explicit.
